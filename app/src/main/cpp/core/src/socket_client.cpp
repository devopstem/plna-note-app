#include "socket_client.hpp"
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <errno.h>
#include <cstring>
#include <algorithm>

#if defined(__ANDROID__)
#include <android/log.h>
#define LOG_TAG "PLNA_Socket"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#include <iostream>
#define LOGD(...) printf(__VA_ARGS__); printf("\n")
#define LOGE(...) fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n")
#endif

namespace plna::net {

    SocketClient::SocketClient() : lastHeartbeat_(std::chrono::steady_clock::now()) {}

    SocketClient::~SocketClient() {
        disconnect();
    }

    bool SocketClient::connect(const std::string& host, int port) {
        if (running_.load()) return true;

        host_ = host;
        port_ = port;
        running_ = true;

        workerThread_ = std::thread(&SocketClient::networkLoop, this);
        return true;
    }

    void SocketClient::disconnect() {
        running_ = false;
        updateStatus(false);

        if (sockfd_ != -1) {
            shutdown(sockfd_, SHUT_RDWR);
            close(sockfd_);
            sockfd_ = -1;
        }

        if (workerThread_.joinable()) {
            workerThread_.join();
        }

        std::lock_guard<std::mutex> lock(queueMutex_);
        std::queue<std::vector<uint8_t>> empty;
        std::swap(sendQueue_, empty);
    }

    void SocketClient::updateStatus(bool newStatus) {
        bool expected = !newStatus;
        if (connected_.compare_exchange_strong(expected, newStatus)) {
            if (onStatusChanged_) {
                onStatusChanged_(newStatus);
            }
        }
    }

    void SocketClient::sendPacket(PacketType type, const std::vector<uint8_t>& data) {
        PacketHeader header;
        header.magic = 0x5450;
        header.type = htons(static_cast<uint16_t>(type)); // Сетевой порядок байт (Big Endian)
        header.length = htonl(static_cast<uint32_t>(data.size()));

        std::vector<uint8_t> packet(sizeof(PacketHeader) + data.size());
        std::memcpy(packet.data(), &header, sizeof(PacketHeader));
        if (!data.empty()) {
            std::memcpy(packet.data() + sizeof(PacketHeader), data.data(), data.size());
        }

        {
            std::lock_guard<std::mutex> lock(queueMutex_);
            sendQueue_.push(std::move(packet));
        }
    }

    void SocketClient::checkHeartbeat() {
        auto now = std::chrono::steady_clock::now();
        if (std::chrono::duration_cast<std::chrono::seconds>(now - lastHeartbeat_).count() >= 30) {
            sendPacket(PacketType::HEARTBEAT, {});
            lastHeartbeat_ = now;
        }
    }

    void SocketClient::networkLoop() {
        std::vector<uint8_t> inboxBuffer;
        std::vector<uint8_t> outboxRemainingBuffer;
        while (running_) {
            if (!connected_) {
                handleReconnect();
                if (!connected_) {
                    std::this_thread::sleep_for(std::chrono::seconds(5));
                    continue;
                }
                inboxBuffer.clear();
                outboxRemainingBuffer.clear();
                lastHeartbeat_ = std::chrono::steady_clock::now();
            }

            checkHeartbeat();

            struct pollfd pfd;
            pfd.fd = sockfd_;
            pfd.events = POLLIN | POLLERR | POLLHUP;

            {
                std::lock_guard<std::mutex> lock(queueMutex_);
                if (!sendQueue_.empty() || !outboxRemainingBuffer.empty()) {
                    pfd.events |= POLLOUT;
                }
            }

            int ret = poll(&pfd, 1, 1000);

            if (ret < 0) {
                if (errno == EINTR) continue;
                updateStatus(false);
                continue;
            }

            if (ret > 0) {
                if (pfd.revents & (POLLERR | POLLHUP)) {
                    updateStatus(false);
                    continue;
                }
                if (pfd.revents & POLLIN) {
                    char readBuf[4096];
                    ssize_t bytesRead = recv(sockfd_, readBuf, sizeof(readBuf), 0);

                    if (bytesRead < 0) {
                        if (errno != EAGAIN && errno != EWOULDBLOCK) updateStatus(false);
                    } else if (bytesRead == 0) {
                        updateStatus(false);
                    } else {
                        bytesReceived_ += bytesRead;
                        inboxBuffer.insert(inboxBuffer.end(), readBuf, readBuf + bytesRead);

                        while (inboxBuffer.size() >= sizeof(PacketHeader)) {
                            PacketHeader header;
                            std::memcpy(&header, inboxBuffer.data(), sizeof(PacketHeader));
                            uint16_t magic = header.magic;
                            uint16_t type = ntohs(header.type);
                            uint32_t length = ntohl(header.length);

                            if (magic != 0x5450) {
                                LOGE("Protocol desync! Searching for next magic bytes...");
                                auto it = std::find(inboxBuffer.begin() + 1, inboxBuffer.end(), 0x54);
                                inboxBuffer.erase(inboxBuffer.begin(), it);
                                continue;
                            }

                            size_t totalSize = sizeof(PacketHeader) + length;
                            if (inboxBuffer.size() < totalSize) break;
                            std::vector<uint8_t> body(inboxBuffer.begin() + sizeof(PacketHeader), inboxBuffer.begin() + totalSize);
                            inboxBuffer.erase(inboxBuffer.begin(), inboxBuffer.begin() + totalSize);
                            lastHeartbeat_ = std::chrono::steady_clock::now();

                            if (onPacketReceived_) {
                                onPacketReceived_(static_cast<PacketType>(type), body);
                            }
                        }
                    }
                }
                if (pfd.revents & POLLOUT) {
                    if (outboxRemainingBuffer.empty()) {
                        std::lock_guard<std::mutex> lock(queueMutex_);
                        if (!sendQueue_.empty()) {
                            outboxRemainingBuffer = std::move(sendQueue_.front());
                            sendQueue_.pop();
                        }
                    }

                    if (!outboxRemainingBuffer.empty()) {
                        ssize_t sent = send(sockfd_, outboxRemainingBuffer.data(), outboxRemainingBuffer.size(), 0);
                        if (sent < 0) {
                            if (errno != EAGAIN && errno != EWOULDBLOCK) updateStatus(false);
                        } else {
                            bytesSent_ += sent;
                            if (static_cast<size_t>(sent) == outboxRemainingBuffer.size()) {
                                outboxRemainingBuffer.clear();
                            } else {
                                outboxRemainingBuffer.erase(outboxRemainingBuffer.begin(), outboxRemainingBuffer.begin() + sent);
                            }
                        }
                    }
                }
            }
        }

        if (sockfd_ != -1) {
            close(sockfd_);
            sockfd_ = -1;
        }
    }

    void SocketClient::handleReconnect() {
        if (sockfd_ != -1) close(sockfd_);

        sockfd_ = socket(AF_INET, SOCK_STREAM, 0);
        if (sockfd_ < 0) return;

        int flags = fcntl(sockfd_, F_GETFL, 0);
        fcntl(sockfd_, F_SETFL, flags | O_NONBLOCK);

        struct sockaddr_in addr;
        std::memset(&addr, 0, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_port = htons(port_);
        inet_pton(AF_INET, host_.c_str(), &addr.sin_addr);

        int res = ::connect(sockfd_, (struct sockaddr*)&addr, sizeof(addr));
        if (res == 0) {
            updateStatus(true);
        } else if (errno == EINPROGRESS) {
            struct pollfd pfd;
            pfd.fd = sockfd_;
            pfd.events = POLLOUT;
            if (poll(&pfd, 1, 3000) > 0) {
                int valopt = 0;
                socklen_t lon = sizeof(int);
                getsockopt(sockfd_, SOL_SOCKET, SO_ERROR, (void*)(&valopt), &lon);
                if (valopt == 0) updateStatus(true);
            }
        }
    }

}

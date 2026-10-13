#pragma once

#include <cstdint>
#include <vector>
#include <string>
#include <atomic>
#include <mutex>
#include <thread>
#include <queue>
#include <functional>
#include <chrono>

namespace plna::net {

#pragma pack(push, 1)
    struct PacketHeader {
        uint16_t magic = 0x5450; // 'TP'
        uint16_t type;
        uint32_t length;
    };
#pragma pack(pop)

    enum class PacketType : uint16_t {
        AUTH = 1,
        SYNC_PLANOGRAM = 2,
        NEW_TASK = 3,
        TASK_STATUS = 4,
        HEARTBEAT = 5
    };

    using OnPacketReceivedCallback = std::function<void(PacketType type, const std::vector<uint8_t>& body)>;
    using OnStatusChangedCallback = std::function<void(bool connected)>;

    class SocketClient {
    public:
        SocketClient();
        ~SocketClient();

        bool connect(const std::string& host, int port);
        void disconnect();

        void sendPacket(PacketType type, const std::vector<uint8_t>& data);

        // Телеметрия
        uint64_t getBytesSent() const { return bytesSent_.load(); }
        uint64_t getBytesReceived() const { return bytesReceived_.load(); }
        void resetStats() { bytesSent_ = 0; bytesReceived_ = 0; }
        void triggerManualHeartbeat() { sendPacket(PacketType::HEARTBEAT, {}); }

        bool isConnected() const { return connected_.load(); }

        void setOnPacketReceived(OnPacketReceivedCallback cb) { onPacketReceived_ = std::move(cb); }
        void setOnStatusChanged(OnStatusChangedCallback cb) { onStatusChanged_ = std::move(cb); }

    private:
        void networkLoop();
        void handleReconnect();
        void updateStatus(bool newStatus);
        void checkHeartbeat();

        int sockfd_ = -1;
        std::atomic<bool> connected_{false};
        std::atomic<bool> running_{false};

        std::string host_;
        int port_ = 0;

        std::thread workerThread_;

        std::queue<std::vector<uint8_t>> sendQueue_;
        mutable std::mutex queueMutex_;

        OnPacketReceivedCallback onPacketReceived_;
        OnStatusChangedCallback onStatusChanged_;

        std::chrono::steady_clock::time_point lastHeartbeat_;

        // Счетчики трафика
        std::atomic<uint64_t> bytesSent_{0};
        std::atomic<uint64_t> bytesReceived_{0};
    };

}

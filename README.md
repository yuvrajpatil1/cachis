# CACHIS

A comprehensive, Redis-like server implementation in Java featuring complete protocol compliance, master-slave replication, transactions, and containerized deployment.

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Framework](https://img.shields.io/badge/Spring-Framework-green.svg)](https://spring.io/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-blue.svg)](https://www.docker.com/)
[![Redis Protocol](https://img.shields.io/badge/RESP-Compatible-red.svg)](https://redis.io/docs/reference/protocol-spec/)

## Features

### Core Redis Commands

- ✅ **PING** - Connection testing
- ✅ **ECHO** - Message echoing
- ✅ **GET/SET** - Key-value operations
- ✅ **INCR** - Atomic increment operations
- ✅ **DEL** - Key deletion
- ✅ **INFO** - Server information

### Advanced Features

- ✅ **Key Expiration** - TTL support with PX flag
- ✅ **Master-Slave Replication** - Multi-replica support
- ✅ **ACID Transactions** - MULTI/EXEC/DISCARD commands
- ✅ **Concurrent Clients** - Thread-safe multi-client handling
- ✅ **Command Propagation** - Real-time replica synchronization
- ✅ **WAIT Command** - Replica acknowledgment system
- ✅ **RESP Protocol** - Full Redis protocol compliance

## Architecture

### Clean Architecture Pattern

```
├── Components/
│   ├── Infra/           # Infrastructure layer
│   │   ├── Client.java          # Client connection management
│   │   ├── ConnectionPool.java  # Connection pooling
│   │   └── Slave.java           # Replica connection wrapper
│   ├── Repository/      # Data persistence layer
│   │   ├── Store.java           # Thread-safe key-value store
│   │   └── Value.java           # Value object with TTL support
│   ├── Server/          # Network layer
│   │   ├── MasterTcpServer.java # Master server implementation
│   │   └── SlaveTcpServer.java  # Replica server implementation
│   └── Service/         # Business logic layer
│       ├── CommandHandler.java  # Redis command processing
│       └── RespSerializer.java  # Protocol serialization
└── Config/
    └── RedisConfig.java         # Configuration management
```

### Key Design Patterns

- **Dependency Injection** - Spring Framework IoC container
- **Command Pattern** - Modular command handling
- **Observer Pattern** - Replication event propagation
- **Template Method** - Transaction processing pipeline

## Technology Stack

- **Java 17+** - Core implementation language
- **Spring Framework** - Dependency injection and IoC
- **Concurrent Collections** - Thread-safe data structures
- **Docker** - Containerization and deployment
- **TCP Sockets** - Network communication
- **RESP Protocol** - Redis serialization protocol

## Installation & Setup

### Prerequisites

- Java 17 or higher
- Docker (for containerized deployment)
- Maven (for building from source)

### Building from Source

```bash
# Clone the repository
git clone <repository-url>
cd java-redis-implementation

# Build the project
mvn clean compile package

# Run locally
java -jar target/redis-server.jar
```

### Docker Deployment

#### Master Server

```bash
# Build Docker image
docker build -t myredis .

# Run master on port 6379
docker run -p 6379:6379 myredis
```

#### Replica Server

```bash
# Run replica on port 6480, connecting to master
docker run -p 6480:6480 myredis --port 6480 --replicaof "host.docker.internal 6379"
```

## Usage Examples

### Basic Commands

```bash
# Connect using any Redis client or TCP client
telnet localhost 6379

# PING command
*1\r\n$4\r\nPING\r\n
+PONG\r\n

# SET command
*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n
+OK\r\n

# GET command
*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n
$3\r\nbar\r\n
```

### PowerShell Testing

```powershell
# Create TCP client
$client = New-Object System.Net.Sockets.TcpClient('localhost', 6379)
$stream = $client.GetStream()
$writer = New-Object System.IO.StreamWriter($stream)
$writer.AutoFlush = $true
$reader = New-Object System.IO.StreamReader($stream)

# Send commands
$pingcommand = "*1`r`n`$4`r`nPING`r`n"
$writer.Write($pingcommand)
$response = $reader.ReadLine()
Write-Host "Server Response: $response"  # Output: +PONG

# Cleanup
$reader.Close()
$writer.Close()
$client.Close()
```

### Transaction Example

```bash
# Start transaction
*1\r\n$5\r\nMULTI\r\n
+OK\r\n

# Queue commands
*3\r\n$3\r\nSET\r\n$3\r\nkey1\r\n$5\r\nvalue1\r\n
+QUEUED\r\n

*2\r\n$4\r\nINCR\r\n$7\r\ncounter\r\n
+QUEUED\r\n

# Execute transaction
*1\r\n$4\r\nEXEC\r\n
*2\r\n+OK\r\n:1\r\n
```

## Replication System

### Master-Slave Setup

The replication system implements Redis's standard 3-phase handshake:

1. **PING Phase** - Replica pings master to establish connection
2. **REPLCONF Phase** - Configuration exchange (port, capabilities)
3. **PSYNC Phase** - Full synchronization initiation

### Automatic Command Propagation

- Write commands automatically propagate to all replicas
- Offset tracking ensures synchronization consistency
- WAIT command provides replica acknowledgment

### Multi-Replica Support

```bash
# Master on 6379
docker run -p 6379:6379 myredis

# Replica 1 on 6480
docker run -p 6480:6480 myredis --port 6480 --replicaof "host.docker.internal 6379"

# Replica 2 on 6481
docker run -p 6481:6481 myredis --port 6481 --replicaof "host.docker.internal 6379"
```

## Transaction Support

### Supported Commands

- `MULTI` - Start transaction
- `EXEC` - Execute queued commands
- `DISCARD` - Abort transaction
- Error handling within transactions

## Performance Features

### Concurrency

- **Thread-safe operations** using `ReentrantReadWriteLock`
- **Concurrent client handling** via `CompletableFuture`
- **Non-blocking replication** for minimal latency impact

## Testing

### Verification Results

The implementation has been successfully tested with:

- Multiple concurrent clients
- Master-slave replication synchronization
- Transaction isolation and atomicity
- Protocol compliance with Redis clients
- Command propagation across replicas
- Error handling and recovery

### Test Coverage

- Unit tests for individual commands
- Integration tests for replication
- End-to-end transaction testing
- Protocol compliance validation

## Configuration

### Command Line Arguments

```bash
# Master server (default)
java -jar redis-server.jar

# Custom port
java -jar redis-server.jar --port 6380

# Replica configuration
java -jar redis-server.jar --port 6480 --replicaof "localhost 6379"
```

## Monitoring & Metrics

### INFO Command Output

```
# Replication
role:master
connected_slaves:2
master_replid:8371b4fb1155b71f4a04d3e1bc3e18c4a990aeeb
master_repl_offset:14

# Server
redis_version:custom-1.0
redis_mode:standalone
tcp_port:6379
```

## Protocol Compliance

### RESP (Redis Serialization Protocol)

- **Simple Strings**: `+OK\r\n`
- **Errors**: `-ERR message\r\n`
- **Integers**: `:42\r\n`
- **Bulk Strings**: `$3\r\nfoo\r\n`
- **Arrays**: `*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n`

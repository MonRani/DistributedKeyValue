
# Paxos Key-Value Store Setup and Execution

This guide explains how to set up and run the Paxos-based Key-Value store using manual compilation and execution of Java files. The system involves multiple servers communicating via RMI to allow operations like PUT, GET, and DELETE on a key-value store.

## Prerequisites

Before you begin, ensure that you have the following installed:

- Java Development Kit (JDK) 8 or above
- Log4j-1.2.17.jar (for logging functionality)

## Steps to Run the Project

### 1. Setup the Project

Ensure that your project folder contains the necessary Java files:
- **`ServerA.java`, `ServerB.java`, `ServerC.java`, `ServerD.java`, `ServerE.java`** for the server instances
- **`KeyStoreClient.java`** for the client operations
- **`log4j-1.2.17.jar`** for logging functionality

### 2. Compile the Project Manually

Open a terminal (or command prompt) and navigate to your project directory:

```bash
cd /path/to/your/project
```

Compile the `.java` files manually using the `javac` command:

```bash
javac -cp ".:/path/to/log4j-1.2.17.jar" *.java
```

This will compile all the Java files in the current directory, including the server and client files.

### 3. Start the Servers

Once the compilation is complete, open multiple terminal windows (one for each server) and start the server instances using the following command:

For **ServerA**:
```bash
java ServerA
```

For **ServerB**:
```bash
java ServerB
```

For **ServerC**:
```bash
java ServerC
```

For **ServerD**:
```bash
java ServerD
```

For **ServerE**:
```bash
java ServerE
```

Each command starts a different server instance. Ensure each terminal window runs a different instance (ServerA, ServerB, etc.).

### 4. Run the Client

In a separate terminal window, run the client with the following command. Replace `localhost` with the appropriate server address and `Server1` with the server to connect to:
But you can change the value of Server from 1 to 5.

```bash
java KeyStoreClient localhost Server1
```

### 5. Perform Operations

Once the client is running, it will automatically perform a series of operations (PUT, GET, DELETE). The client will prompt for additional operations, allowing you to input key-value pairs or commands for further actions. You can choose to perform `PUT`, `GET`, or `DEL` operations by entering the corresponding option. The client will interact with the server to perform the desired operations on the key-value store.

---

# Docker Setup

## Dockerfile

```Dockerfile
# Step 1: Use a base image with Java installed
FROM openjdk:21-slim

# Step 2: Set the working directory inside the container
WORKDIR /app

# Step 3: Copy all project files into the container
COPY . /app

# Step 4: Compile all Java source files
RUN javac src/*.java

# Step 5: Allow overriding the command for running the client or servers
CMD ["java", "-cp", "src", "KeyStoreClient", "localhost", "Server1", "Server2", "Server3", "Server4", "Server5"]
```

## Docker Commands

### Build the Docker Images

To build the images for all services defined in the `docker-compose.yml`, run:

```bash
docker-compose build
```

### Start the Docker Containers

To start the Docker containers and run the services, use the following command:

```bash
docker-compose up
```

### Troubleshooting

If you encounter issues, check the logs for each service to ensure they are running as expected. Use `docker-compose logs <service-name>` to view logs, for example:

```bash
docker-compose logs server-a
```


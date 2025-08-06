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

# Backend Development Setup

## Prerequisites

- Java 21
- Docker and Docker Compose
- Gradle wrapper included in the project
- IDE: IntelliJ IDEA recommended

## Setup

Clone the repository and navigate to the backend directory:

```bash
cd sprintstart-backend
```

## Environment Files

The backend uses two different local environment files:

```text
.env            -> used by Docker Compose
run.local.env   -> used when running the backend locally
```

The real environment files must not be committed!!!

## Docker Compose Environment

Create a local `.env` file based on `.env.example`:

```bash
cp .env.example .env
```

Example `.env`:

```env
POSTGRES_DB=sprintstart
POSTGRES_USER=sprintstart
POSTGRES_PASSWORD=change-me
```

Docker Compose reads `.env` automatically when running commands.

## Local Backend Environment

Create a local `run.local.env` file based on `run.local.env.example`:

```bash
cp run.local.env.example run.local.env
```

This file is used when running the backend locally

The application uses these variables in `application.yml`:

## Run Database in Docker and Backend Locally

Use this option during normal development when running the Spring Boot backend separately from the database.

Start only the database:

```bash
docker compose up -d database
```

Stop the database

```bash
docker compose down -v database
```

The local backend should use these values from `run.local.env`:

Run the backend locally:

```bash
./gradlew bootRun
```

## Run Database and Backend in Docker

Use this option to run the complete backend setup inside Docker Compose.

Start database and backend:

```bash
docker compose up -d
```
Remove -d if you want to have the outputs of docker in the terminal you are using

To stop the database and backend:

```bash
docker compose down -v
```
Warning: `docker compose down -v` deletes the local PostgreSQL data.
Remove -v if you **don't** want the database volume to persist

## Useful Commands

Show running containers:

```bash
docker ps
```

Show logs:

```bash
docker compose logs -f
```

Show backend logs only:

```bash
docker compose logs -f backend
```

Show database logs only:

```bash
docker compose logs -f database
```

Remove the `-f` to only get the outputs once and immediately close the terminal to the compose again.

## Build Manually

Build the backend:

```bash
./gradlew build
```

Build the bootable Spring Boot JAR:

```bash
./gradlew bootJar
```

Run the JAR:

```bash
java -jar build/libs/*.jar
```

Build the Docker image manually:

```bash
docker build -t sprintstart-backend .
```
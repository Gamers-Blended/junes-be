pipeline {
    agent {
        label 'docker-agent'
    }

    environment {
        APP_NAME = 'junes'
        COMPOSE_FILE = 'docker-compose.app.yml'
        DOCKER_HOST = 'unix:///var/run/docker.sock'

        // Tell Maven to use mounted cache directory
        MAVEN_OPTS = '-Dmaven.repo.local=${HOME}/.m2/repository'

        // For docker-compose
        POSTGRES_USER            = credentials('POSTGRES_USER')
        POSTGRES_PASSWORD        = credentials('POSTGRES_PASSWORD')
        MONGO_INITDB_ROOT_USERNAME = credentials('MONGO_INITDB_ROOT_USERNAME')
        MONGO_INITDB_ROOT_PASSWORD = credentials('MONGO_INITDB_ROOT_PASSWORD')
        PGADMIN_DEFAULT_EMAIL    = credentials('PGADMIN_DEFAULT_EMAIL')
        PGADMIN_DEFAULT_PASSWORD = credentials('PGADMIN_DEFAULT_PASSWORD')
        RABBITMQ_DEFAULT_USER    = credentials('RABBITMQ_DEFAULT_USER')
        RABBITMQ_DEFAULT_PASS    = credentials('RABBITMQ_DEFAULT_PASS')
        KAFKA_CLUSTER_ID         = credentials('KAFKA_CLUSTER_ID')

        // application.properties
        MAILGUN_API_KEY_2 = credentials('MAILGUN_API_KEY_2')
        MAILGUN_DOMAIN = credentials('MAILGUN_DOMAIN')
        MAILGUN_FROM_EMAIL = credentials('MAILGUN_FROM_EMAIL')
        IMAGE_URL_PREFIX = credentials('IMAGE_URL_PREFIX')
        JWT_ACCESS_SECRET = credentials('JWT_ACCESS_SECRET')
        JWT_EMAIL_SECRET = credentials('JWT_EMAIL_SECRET')
        RABBITMQ_HOST = credentials('RABBITMQ_HOST')
        RABBITMQ_PORT = credentials('RABBITMQ_PORT')
        RABBITMQ_USERNAME = credentials('RABBITMQ_USERNAME')
        RABBITMQ_PASSWORD = credentials('RABBITMQ_PASSWORD')
        KAFKA_BOOTSTRAP_SERVERS = credentials('KAFKA_BOOTSTRAP_SERVERS')
        KAFKA_CONSUMER_GROUP = credentials('KAFKA_CONSUMER_GROUP')
        POSTGRES_URL = credentials('POSTGRES_URL')
        POSTGRES_USERNAME = credentials('POSTGRES_USERNAME')
        MONGODB_URI = credentials('MONGODB_URI')

        PROD_CONFIG = credentials('4a919fc7-01bb-438b-9e14-ae05845032d4')
    }

    options {
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Start Dependencies') {
            steps {
                sh """
                    docker compose -f ${env.COMPOSE_FILE} down

                    docker compose -f ${env.COMPOSE_FILE} up -d postgres mongodb redis rabbitmq kafka

                    echo 'Waiting for services to be ready...'
                    sleep 15
                """
            }
        }

        stage('Build Spring Boot App') {
            steps {
                sh """
                    docker compose -f ${env.COMPOSE_FILE} run --rm junes-app \
                        mvn clean compile -Dmaven.repo.local=/root/.m2/repository
                """
            }
        }

        stage('Run Tests') {
            steps {
                sh """
                    docker compose -f ${env.COMPOSE_FILE} run --rm \
                        --build \
                        --entrypoint "" \
                        junes-app \
                        mvn test
                """
            }
        }

        stage('Package Application') {
            steps {
                sh """
                    mvn package -DskipTests
                """
            }
        }

        stage('Build Docker Image') {
            steps {
                sh """
                    docker build -t ${APP_NAME}:${BUILD_NUMBER} -t ${APP_NAME}:latest .
                """
            }
        }

        stage('Deploy App') {
            when { branch 'main' }
            steps {
                sh """
                    # Stop existing app container if running
                    docker compose -f ${env.COMPOSE_FILE} stop ${APP_NAME} || true
                    docker compose -f ${env.COMPOSE_FILE} rm -f ${APP_NAME} || true

                    # Update the image reference in docker-compose or just run
                    docker compose -f ${env.COMPOSE_FILE} up -d ${APP_NAME}

                    sleep 10
                    curl -f http://localhost:8080/actuator/health || true
                """
            }
            post {
                always {
                    sh "docker compose -f ${env.COMPOSE_FILE} stop postgres mongodb redis rabbitmq kafka || true"
                }
            }
        }

    }

    post {
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline failed. Check logs for details.'
        }
    }
}
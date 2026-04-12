pipeline {
    agent {
        label 'docker-agent'
    }

    environment {
        APP_NAME = 'junes'
        COMPOSE_FILE = 'docker-compose.app.yml'
        DOCKER_HOST = 'unix:///var/run/docker.sock'

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

        PROD_CONFIG = credentials('4a919fc7-01bb-438b-9e14-ae05845032d4')
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
                    docker compose -f ${COMPOSE_FILE} down

                    docker compose -f ${COMPOSE_FILE} up -d postgres mongodb redis rabbitmq kafka

                    echo 'Waiting for services to be ready...'
                    sleep 15
                """
            }
        }

        stage('Build Spring Boot App') {
            steps {
                sh """
                    chmod +x mvnw 2>/dev/null || true
                    mvn clean compile
                """
            }
        }

        stage('Run Tests') {
            steps {
                sh "mvn test"
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
                    docker compose -f ${COMPOSE_FILE} stop ${APP_NAME} || true
                    docker compose -f ${COMPOSE_FILE} rm -f ${APP_NAME} || true

                    # Update the image reference in docker-compose or just run
                    docker compose -f ${COMPOSE_FILE} up -d ${APP_NAME}

                    sleep 10
                    curl -f http://localhost:8080/actuator/health || true
                """
            }
        }
    }

    post {
        always {
            // Cleanup: stop test dependencies but keep volumes
            sh """
                docker compose -f ${COMPOSE_FILE} stop postgres mongodb redis rabbitmq kafka || true
            """
        }
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline failed. Check logs for details.'
        }
    }
}
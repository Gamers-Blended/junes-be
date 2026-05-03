pipeline {
    agent {
        label 'docker-agent'
    }

    environment {
        APP_NAME = 'junes'
        COMPOSE_FILE = 'docker-compose.app.yml'
        DOCKER_HOST = 'unix:///var/run/docker.sock'

        // Tell Maven to use mounted cache directory
        MAVEN_OPTS = '-Dmaven.repo.local=/home/jenkins/.m2/repository'

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

    options {
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Setup Environment') {
            steps {
                script {
                    env.HOST_WORKSPACE = sh(script: 'pwd', returnStdout: true).trim()
                    env.HOST_DOCKER_GID = sh(script: "stat -c '%g' /var/run/docker.sock", returnStdout: true).trim()
                }
            }
        }

        stage('Build Agent') {
            steps {
                sh """
                    export DOCKER_BUILDKIT=0
                    docker build --build-arg DOCKER_GID=${env.HOST_DOCKER_GID} \
                        -t custom-jenkins-agent \
                        -f Dockerfile.agent .
                """
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
                    mvn clean compile -Dmaven.repo.local=/home/jenkins/.m2/repository
                """
            }
        }

        stage('Run Tests') {
            steps {
                // application.properties
                withCredentials([
                    string(credentialsId: 'MAILGUN_API_KEY_2', variable: 'MAILGUN_API_KEY_2'),
                    string(credentialsId: 'MAILGUN_DOMAIN', variable: 'MAILGUN_DOMAIN'),
                    string(credentialsId: 'MAILGUN_FROM_EMAIL', variable: 'MAILGUN_FROM_EMAIL'),
                    string(credentialsId: 'IMAGE_URL_PREFIX', variable: 'IMAGE_URL_PREFIX'),
                    string(credentialsId: 'JWT_ACCESS_SECRET', variable: 'JWT_ACCESS_SECRET'),
                    string(credentialsId: 'JWT_EMAIL_SECRET', variable: 'JWT_EMAIL_SECRET'),
                    string(credentialsId: 'RABBITMQ_HOST', variable: 'RABBITMQ_HOST'),
                    string(credentialsId: 'RABBITMQ_PORT', variable: 'RABBITMQ_PORT'),
                    string(credentialsId: 'RABBITMQ_USERNAME', variable: 'RABBITMQ_USERNAME'),
                    string(credentialsId: 'RABBITMQ_PASSWORD', variable: 'RABBITMQ_PASSWORD'),
                    string(credentialsId: 'KAFKA_BOOTSTRAP_SERVERS', variable: 'KAFKA_BOOTSTRAP_SERVERS'),
                    string(credentialsId: 'KAFKA_CONSUMER_GROUP', variable: 'KAFKA_CONSUMER_GROUP'),
                    string(credentialsId: 'POSTGRES_URL', variable: 'POSTGRES_URL'),
                    string(credentialsId: 'POSTGRES_USERNAME', variable: 'POSTGRES_USERNAME'),
                    string(credentialsId: 'MONGODB_URI', variable: 'MONGODB_URI')
                ]) {
                    sh '''
                        mvn test \
                            -Dmaven.repo.local=/home/jenkins/.m2/repository \
                            -DMAILGUN_API_KEY_2="$MAILGUN_API_KEY_2" \
                            -DMAILGUN_DOMAIN="$MAILGUN_DOMAIN" \
                            -DMAILGUN_FROM_EMAIL="$MAILGUN_FROM_EMAIL" \
                            -DIMAGE_URL_PREFIX="$IMAGE_URL_PREFIX" \
                            -DJWT_ACCESS_SECRET="$JWT_ACCESS_SECRET" \
                            -DJWT_EMAIL_SECRET="$JWT_EMAIL_SECRET" \
                            -DRABBITMQ_HOST="$RABBITMQ_HOST" \
                            -DRABBITMQ_PORT="$RABBITMQ_PORT" \
                            -DRABBITMQ_USERNAME="$RABBITMQ_USERNAME" \
                            -DRABBITMQ_PASSWORD="$RABBITMQ_PASSWORD" \
                            -DKAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS" \
                            -DKAFKA_CONSUMER_GROUP="$KAFKA_CONSUMER_GROUP" \
                            -DPOSTGRES_URL="$POSTGRES_URL" \
                            -DPOSTGRES_USERNAME="$POSTGRES_USERNAME" \
                            -DMONGODB_URI="$MONGODB_URI"
                    '''
                }
            }
        }

        stage('Package Application') {
            steps {
                sh """
                    mvn package -DskipTests -Dmaven.repo.local=/home/jenkins/.m2/repository
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
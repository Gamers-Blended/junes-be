pipeline {
    agent {
        label 'docker-agent'
    }

    environment {
        APP_NAME = 'junes-app'
        COMPOSE_FILE = 'docker-compose.app.yml'
        COMPOSE_PROJECT = 'junes'
        DOCKER_HOST = 'unix:///var/run/docker.sock'

        // Tell Maven to use mounted cache directory
        MAVEN_OPTS = '-Dmaven.repo.local=/home/jenkins/.m2/repository'
        MVN           = 'mvn -Dmaven.repo.local=/home/jenkins/.m2/repository'

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

        // For application.properties
        MAILGUN_API_KEY_2       = credentials('MAILGUN_API_KEY_2')
        MAILGUN_DOMAIN          = credentials('MAILGUN_DOMAIN')
        MAILGUN_FROM_EMAIL      = credentials('MAILGUN_FROM_EMAIL')
        IMAGE_URL_PREFIX        = credentials('IMAGE_URL_PREFIX')
        JWT_ACCESS_SECRET       = credentials('JWT_ACCESS_SECRET')
        JWT_EMAIL_SECRET        = credentials('JWT_EMAIL_SECRET')
        RABBITMQ_HOST           = credentials('RABBITMQ_HOST')
        RABBITMQ_PORT           = credentials('RABBITMQ_PORT')
        RABBITMQ_USERNAME       = credentials('RABBITMQ_USERNAME')
        RABBITMQ_PASSWORD       = credentials('RABBITMQ_PASSWORD')
        KAFKA_BOOTSTRAP_SERVERS = credentials('KAFKA_BOOTSTRAP_SERVERS')
        KAFKA_CONSUMER_GROUP    = credentials('KAFKA_CONSUMER_GROUP')
        POSTGRES_URL            = credentials('POSTGRES_URL')
        POSTGRES_USERNAME       = credentials('POSTGRES_USERNAME')
        MONGODB_URI             = credentials('MONGODB_URI')

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
                sh "docker compose -p ${COMPOSE_PROJECT} -f ${env.COMPOSE_FILE} up -d postgres mongodb redis rabbitmq kafka"
            }
        }

        stage('Run Unit Tests') {
            steps {
                sh "${MVN} clean test"
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Package Application') {
            steps {
                sh """
                    ${MVN} package -DskipTests

                    echo "==> Verifying JAR exists before docker build:"
                    ls -lh target/*.jar
                """
            }
        }

        stage('Build Docker Image') {
            steps {
                sh """
                    docker build --build-arg DOCKER_GID=${env.HOST_DOCKER_GID} \
                    -t ${APP_NAME}:${BUILD_NUMBER} \
                    -t ${APP_NAME}:latest \
                    -f Dockerfile.agent .
                """
            }
        }

        stage('Deploy App') {
            when { branch 'main' }
            steps {
                sh """
                    docker compose -p ${COMPOSE_PROJECT} -f ${env.COMPOSE_FILE} up -d ${APP_NAME}

                    docker compose -p ${COMPOSE_PROJECT} -f ${env.COMPOSE_FILE} logs ${APP_NAME}

                    echo "Waiting for junes-app to be healthy..."
                    ATTEMPTS=0
                    MAX_ATTEMPTS=5
                    until docker inspect --format='{{.State.Health.Status}}' \$(docker compose -p ${COMPOSE_PROJECT} -f ${env.COMPOSE_FILE} ps -q ${APP_NAME}) | grep -q 'healthy'; do
                        ATTEMPTS=\$((ATTEMPTS + 1))
                        if [ \$ATTEMPTS -ge \$MAX_ATTEMPTS ]; then
                            echo "ERROR: App did not become healthy after \${MAX_ATTEMPTS} attempts"
                            docker compose -p ${COMPOSE_PROJECT} -f ${env.COMPOSE_FILE} logs ${APP_NAME}
                            exit 1
                        fi
                        echo "Attempt \$ATTEMPTS/\$MAX_ATTEMPTS — waiting 10s..."
                        sleep 10
                    done
                    echo "App is healthy!"
                """
            }
        }

    }

    post {
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline failed. Check logs for details.'
            sh "docker compose -f ${env.COMPOSE_FILE} down"
        }
    }
}
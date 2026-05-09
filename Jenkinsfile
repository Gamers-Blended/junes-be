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
                    env.GIT_SHORT_SHA    = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG        = "${BUILD_NUMBER}-${env.GIT_SHORT_SHA}"
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

        stage('Build Production Image') {
            steps {
                sh """
                    docker build \
                        -t ${APP_NAME}:${env.IMAGE_TAG} \
                        -t ${APP_NAME}:latest \
                        -f Dockerfile .
                """
            }
        }

        stage('Deploy App') {
            when { branch 'main' }
            steps {
                sh """
                    set -e

                    COMPOSE_CMD="docker compose -p ${COMPOSE_PROJECT} -f ${env.COMPOSE_FILE}"

                    echo "==> Tagging current image as rollback target..."
                    docker tag ${APP_NAME}:latest ${APP_NAME}:rollback || true

                    echo "==> Deploying image ${APP_NAME}:${env.IMAGE_TAG}..."
                    BUILD_NUMBER=${env.IMAGE_TAG} \$COMPOSE_CMD up -d --no-deps ${APP_NAME}

                    echo "==> Waiting for JVM to initialize (start_period)..."
                    sleep 60

                    echo "==> Polling health status..."
                    ATTEMPTS=0
                    MAX_ATTEMPTS=5
                    until [ "\$(docker inspect --format='{{.State.Health.Status}}' \$(\$COMPOSE_CMD ps -q --status running ${APP_NAME} | head -1))" = "healthy" ]; do
                        ATTEMPTS=\$((ATTEMPTS + 1))
                        if [ \$ATTEMPTS -ge \$MAX_ATTEMPTS ]; then
                            echo "ERROR: App did not become healthy after \$((MAX_ATTEMPTS * 15))s"
                            echo "==> Last 100 lines of logs:"
                            \$COMPOSE_CMD logs --tail=100 ${APP_NAME}
                            echo "==> Container inspect:"
                            docker inspect \$(\$COMPOSE_CMD ps -q --status running ${APP_NAME} | head -1)
                            exit 1
                        fi
                        echo "Attempt \$ATTEMPTS/\$MAX_ATTEMPTS — not yet healthy, waiting 15s..."
                        sleep 15
                    done

                    echo "==> App is healthy! Deployed ${APP_NAME}:${env.IMAGE_TAG}"
                    echo "==> Recent logs:"
                    \$COMPOSE_CMD logs --tail=50 ${APP_NAME}
                """
            }
            post {
                failure {
                    sh """
                        echo "==> Deploy failed — rolling back to previous image..."
                        docker compose -p ${COMPOSE_PROJECT} -f ${env.COMPOSE_FILE} \
                            up -d --no-deps \
                            -e BUILD_NUMBER=rollback \
                            ${APP_NAME} || true

                        echo "==> Full logs from failed deploy:"
                        docker compose -p ${COMPOSE_PROJECT} -f ${env.COMPOSE_FILE} \
                            logs --tail=200 ${APP_NAME} || true
                    """
                }
            }
        }

    }

    post {
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            script {
                def proj = env.COMPOSE_PROJECT ?: 'junes'
                def file = env.COMPOSE_FILE ?: 'docker-compose.app.yml'
                def app  = env.APP_NAME ?: 'junes-app'

                sh "docker compose -p ${proj} -f ${file} ps 2>&1 || true"

                // Logs via compose (needs correct project name)
                sh "docker compose -p ${proj} -f ${file} logs --timestamps --tail=300 ${app} 2>&1 || true"

                // Fallback: direct docker logs by full container name
                sh "docker logs --timestamps --tail=300 ${proj}-${app}-1 2>&1 || true"

                // Inspect exit code if container exists
                sh "docker inspect ${proj}-${app}-1 --format='ExitCode={{.State.ExitCode}} Error={{.State.Error}}' 2>&1 || true"

                sh "docker compose -p ${proj} -f ${file} down || true"
            }
        }
        always {
            script {
                def proj = env.COMPOSE_PROJECT ?: 'junes'
                def file = env.COMPOSE_FILE ?: 'docker-compose.app.yml'
                sh "docker compose -p ${proj} -f ${file} logs --timestamps 2>&1 > build-logs.txt || true"
            }
            archiveArtifacts artifacts: 'build-logs.txt', allowEmptyArchive: true
        }
    }
}
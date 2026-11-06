pipeline {
    agent any

    environment {
        DOCKER_HUB_USER    = 'aishani87'
        IMAGE_NAME         = 'cloud-demo-app'
        IMAGE_TAG          = "${BUILD_NUMBER}"
        DOCKER_CREDS_ID    = 'docker-hub-credentials'
        KUBECONFIG_CRED_ID = 'k8s-kubeconfig'
    }

    tools {
        maven 'Maven3'
        jdk 'JDK21'
    }

    stages {
        stage('Checkout Source Code') {
            steps {
                echo 'Checking out source code from Git repository...'
                checkout scm
            }
        }

        stage('Security Scan (Gitleaks)') {
            steps {
                echo 'Running Gitleaks secret detection scan on latest commit...'
                sh '''
                    export MSYS_NO_PATHCONV=1
                    docker run --rm \
                      -v "$PWD":/repo \
                      zricethezav/gitleaks:latest \
                      detect \
                      --source="/repo" \
                      --log-opts="-1" \
                      --verbose \
                      --report-format=json \
                      --report-path="/repo/gitleaks-report.json" || EXIT_CODE=$?

                    if [ -n "$EXIT_CODE" ] && [ "$EXIT_CODE" -ne 0 ]; then
                        echo "CRITICAL: Hardcoded secrets detected by Gitleaks! Failing build."
                        exit 1
                    fi
                    echo "Gitleaks scan passed. No secrets found in latest commit."
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'gitleaks-report.json', allowEmptyArchive: true
                }
            }
        }

        stage('Compile & Test (Maven)') {
            steps {
                echo 'Compiling code and running unit tests...'
                sh 'mvn clean test'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package Application') {
            steps {
                echo 'Packaging application JAR...'
                sh 'mvn package -DskipTests'
                archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
            }
        }

        stage('Build & Tag Docker Image') {
            steps {
                echo "Building Docker image: ${DOCKER_HUB_USER}/${IMAGE_NAME}:${IMAGE_TAG}..."
                sh """
                    docker build -t ${DOCKER_HUB_USER}/${IMAGE_NAME}:${IMAGE_TAG} .
                    docker tag ${DOCKER_HUB_USER}/${IMAGE_NAME}:${IMAGE_TAG} ${DOCKER_HUB_USER}/${IMAGE_NAME}:latest
                """
            }
        }

        stage('Push Docker Image') {
            steps {
                echo 'Pushing Docker image to registry...'
                withCredentials([usernamePassword(credentialsId: "${DOCKER_CREDS_ID}", usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh """
                        echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin
                        docker push ${DOCKER_HUB_USER}/${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${DOCKER_HUB_USER}/${IMAGE_NAME}:latest
                    """
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                echo 'Deploying application to Kubernetes via kubectl...'
                withCredentials([file(credentialsId: "${KUBECONFIG_CRED_ID}", variable: 'KUBE_FILE')]) {
            
                    withEnv([
                        "TARGET_IMAGE=${DOCKER_HUB_USER.toLowerCase()}/${IMAGE_NAME.toLowerCase()}:${IMAGE_TAG}"
                    ]) {
                        sh '''
                            export KUBECONFIG="$KUBE_FILE"
                            kubectl apply -f k8s/namespace.yml
                            kubectl apply -f k8s/service.yml
                            kubectl apply -f k8s/deployment.yml
                            kubectl set image deployment/cloud-demo-app cloud-demo-app="$TARGET_IMAGE" -n devops-demo
                        '''
                    }
                }
            }
        }

        stage('Verify Deployment Status') {
            steps {
                echo 'Checking rollout status and service endpoint...'
                withCredentials([file(credentialsId: "${KUBECONFIG_CRED_ID}", variable: 'KUBECONFIG_FILE')]) {
                    sh """
                        export KUBECONFIG=\$KUBECONFIG_FILE
                        kubectl rollout status deployment/cloud-demo-app -n devops-demo --timeout=120s
                        kubectl get pods -n devops-demo -l app=cloud-demo-app
                        kubectl get svc cloud-demo-service -n devops-demo
                    """
                }
            }
        }
    }

    post {
        success {
            echo "CI/CD Pipeline executed successfully! App deployed with tag ${IMAGE_TAG}."
        }
        failure {
            echo 'Pipeline failed. Check stage logs for details.'
        }
        always {
            sh "docker logout || true"
        }
    }
}
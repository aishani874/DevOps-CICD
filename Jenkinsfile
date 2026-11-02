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
                withCredentials([file(credentialsId: "${KUBECONFIG_CRED_ID}", variable: 'KUBECONFIG_FILE')]) {
                    sh """
                        export KUBECONFIG="${KUBECONFIG_FILE}"

                        # Ensure namespace exists
                        kubectl apply -f k8s/namespace.yml

                        # Ensure service is created/updated
                        kubectl apply -f k8s/service.yml

                        # Apply base deployment
                        kubectl apply -f k8s/deployment.yml

                        # Update the image to trigger rolling deployment with the current build tag
                        kubectl set image deployment/cloud-demo-app cloud-demo-app=${DOCKER_HUB_USER}/${IMAGE_NAME}:${IMAGE_TAG} -n devops-demo
                    """
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
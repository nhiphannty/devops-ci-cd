#!/usr/bin/env groovy
void call(String name) {
    String registry = 'pdevopsacr.azurecr.io'
    String acrCredential = 'acr-token'
    String k8sCredential = 'aks'
    String namespace = 'pdevops'

    stage ('Prepare Package') {
        script {
            writeFile file: '.ci/Dockerfile', text: libraryResource('nodejs/Dockerfile')
            writeFile file: '.ci/deployment.yml', text: libraryResource('deploy/aks/deployment.yml')
            writeFile file: '.ci/service.yml', text: libraryResource('deploy/aks/service.yml')
        }
    }

    stage ('Build') {
        docker.build("${registry}/${name}:latest", "--force-rm --no-cache -f ./.ci/Dockerfile \
        --build-arg IMG_VERSION=${BUILD_NUMBER} .")
    }

    stage ('Push Docker Images') {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: acrCredential,
            usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            docker.withRegistry("https://${registry}", acrCredential) {
                sh "docker login ${registry} -u ${USERNAME} -p ${PASSWORD}"
                sh "docker push ${registry}/${name}:latest"
            }
        }
    }

    stage ("Deploy To K8S") {
        kubeconfig(credentialsId: k8sCredential, serverUrl: '') {
            sh "export registry=${registry}; export appname=${name}; export tag=latest; \
            envsubst < .ci/deployment.yml > deployment.yml; envsubst < .ci/service.yml > service.yml"
            sh "kubectl apply -f deployment.yml -n ${namespace}"
            sh "kubectl apply -f service.yml -n ${namespace}"
        }
    }
}
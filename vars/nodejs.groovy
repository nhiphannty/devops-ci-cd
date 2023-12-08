#!/usr/bin/env groovy
void call(name) {
    String baseImage = "mcr.microsoft.com/dotnet/sdk"
    String registry = "pdevopsacr.azurecr.io"
    String acrCredential = 'acr-token'
    String k8sCredential = 'akstest'
    String namespace = "pdevops"

    stage ('Prepare Package') {
        script {
            writeFile file: '.ci/Dockerfile', text: libraryResource('nodejs/Dockerfile.SDK')
            writeFile file: '.ci/deployment.yml', text: libraryResource('deploy/aks/deployment.yml')
            writeFile file: '.ci/service.yml', text: libraryResource('deploy/aks/service.yml')
        }
    }

    stage ("Build and Publish") {
        docker.build("${name}:${BUILD_NUMBER}", "--force-rm --no-cache -f ./.ci/Dockerfile.SDK \
        --build-arg BASEIMG=${baseImage} --build-arg IMG_VERSION=${baseTag} ${WORKSPACE}") 
    }


    stage ("Publish Package") {
        docker.build("${registry}/${name}:${BUILD_NUMBER}", "--force-rm --no-cache -f ./.ci/Dockerfile.Runtime.API \
        --build-arg BASEIMG=${name}-sdk --build-arg IMG_VERSION=${BUILD_NUMBER} \
        --build-arg ENTRYPOINT=${runtime} --build-arg PUBLISH_PROJ=${publishProject} --build-arg RUNIMG=${baseImage} --build-arg RUNVER=${baseTag} .")
    }

    stage ("Push Docker Images") {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: acrCredential, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            docker.withRegistry("https://${registry}", acrCredential ) {
                sh "docker login ${registry} -u ${USERNAME} -p ${PASSWORD}"
                sh "docker push ${registry}/${name}:${BUILD_NUMBER}"
            }
        }
    }

    stage ("Deploy To K8S") {
        kubeconfig(credentialsId: k8sCredential, serverUrl: '') {
            sh "export registry=${registry}; export appname=${name}; export tag=${BUILD_NUMBER}; \
            envsubst < .ci/deployment.yml > deployment.yml; envsubst < .ci/service.yml > service.yml"
            sh "kubectl apply -f deployment.yml -n ${namespace}"
            sh "kubectl apply -f service.yml -n ${namespace}"
        }
    }
}
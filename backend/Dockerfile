FROM openjdk:11-jdk

WORKDIR /opt/springboot

ADD ./build/libs/sawyou-web-project-1.0.jar /opt/springboot/app.jar

ENTRYPOINT ["java","-jar","app.jar"]

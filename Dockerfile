FROM --platform=linux/amd64 ibm-semeru-runtimes:open-17-jre

RUN mkdir /opt/app

COPY target/topics-usage-monitor-0.0.1-jar-with-dependencies.jar /opt/app

WORKDIR /opt/app

CMD java -Dorg.slf4j.simpleLogger.defaultLogLevel=$LOG_LEVEL -Dorg.slf4j.simpleLogger.showDateTime=true -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss.SSS -jar topics-usage-monitor-0.0.1-jar-with-dependencies.jar

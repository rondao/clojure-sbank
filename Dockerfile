FROM clojure:temurin-18-lein-alpine

RUN apk add maven

WORKDIR /usr/src/cognitect-dev-tool
COPY cognitect-dev-tools .

RUN ./install

RUN mkdir /root/.datomic/
RUN echo "{:storage-dir \"/tmp/databases/\"}" > /root/.datomic/dev-local.edn

WORKDIR /usr/src/app/
COPY . .

ARG LEIN_USERNAME 
ARG LEIN_PASSWORD

RUN lein sub install

ENTRYPOINT ["lein", "sub", "-s"]
CMD []
(ns sbank.http
  (:import [java.time Duration]
           [com.twitter.finagle Http Service]
           [com.twitter.util Await Awaitable Future]
           [com.twitter.finagle.http Response]
           [org.apache.kafka.clients.consumer ConsumerConfig KafkaConsumer]
           [org.apache.kafka.clients.producer ProducerConfig ProducerRecord KafkaProducer])
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]))

(s/def :bank/account pos-int?)
(s/def :bank/amount pos-int?)

(s/def :bank/data (s/keys :req [:bank/account :bank/amount]))
(s/def :bank/deposit :bank/data)
(s/def :bank/withdrawn :bank/data)
(s/def :bank/statement :bank/account)

(defn json-parser
  [json-str]
  (try (json/read-str json-str :key-fn keyword)
       (catch Exception e {})))

(defn json-serialize
  [json-str]
  (json/write-str json-str :key-fn #(subs (str %) 1)))

(def consumer-properties
  {ConsumerConfig/BOOTSTRAP_SERVERS_CONFIG "localhost:9092"
   ConsumerConfig/GROUP_ID_CONFIG "bank"
   ConsumerConfig/KEY_DESERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringDeserializer"
   ConsumerConfig/VALUE_DESERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringDeserializer"})

(def producer-properties
  {ProducerConfig/BOOTSTRAP_SERVERS_CONFIG "localhost:9092"
   ProducerConfig/KEY_SERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringSerializer"
   ProducerConfig/VALUE_SERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringSerializer"})

(def producer
  (KafkaProducer. producer-properties))

(defn make-statement-service
  [data-parser]
  (proxy [Service] []
    (apply [request]
      (with-open [consumer (KafkaConsumer. consumer-properties)]
        (.subscribe consumer ["read-statement"])
        (let [response (Response/apply)
              statement-order (data-parser (.getContentString request))]
          (println "Statement Order" statement-order)
          (.send producer (ProducerRecord. "bank-statement" (json-serialize statement-order)))
          (let [statement-value (loop [records []]
                                  (let [record (filter #(= (str statement-order) (.key %)) records)]
                                    (println record)
                                    (if (empty? record)
                                      (recur (seq (.poll consumer (Duration/ofSeconds 1))))
                                      (json-parser (.value (first record))))))]
            (.setContentString
             response
             (str "Statement: " statement-value))
            (Future/value response)))))))
        
     
(defn make-deposit-service
  [data-parser]
  (proxy [Service] []
    (apply [request]
      (let [response (Response/apply)
            deposit-order (data-parser (.getContentString request))]
        (println "Deposit Order" deposit-order)
        (.setContentString
         response
         (if (s/valid? :bank/deposit deposit-order)
           (format "Success Deposit! New value %s."
                   (.send producer (ProducerRecord. "bank-deposit" (json-serialize deposit-order))))
           "Failed! Bad Request."))
        (Future/value response)))))

(defn make-withdrawn-service
  [data-parser]
  (proxy [Service] []
    (apply [request]
      (let [response (Response/apply)
            withdrawn-order (data-parser (.getContentString request))]
        (println "Withdrawn Order" withdrawn-order)
        (println "Withdrawn Order STR" (json-serialize withdrawn-order))
        (.setContentString
         response
         (if (s/valid? :bank/withdrawn withdrawn-order)
           (format "Success Withdrawn! New value %s."
                   (.send producer (ProducerRecord. "bank-withdrawn" (json-serialize withdrawn-order))))
           "Failed! Bad Request."))
        (Future/value response)))))

(defn serve-service
  [port service]
  (Http/serve (str ":" port) service))

(defn -main
  [& args]
  (let [deposit-port (Integer/parseInt (first args))
        withdrawn-port (inc deposit-port)
        statement-port (inc withdrawn-port)
        deposit-service (serve-service deposit-port (make-deposit-service json-parser))
        withdrawn-service (serve-service withdrawn-port (make-withdrawn-service json-parser))
        statement-service (serve-service statement-port (make-statement-service json-parser))]
    (Await/all
     (into-array Awaitable [deposit-service withdrawn-service statement-service]))))
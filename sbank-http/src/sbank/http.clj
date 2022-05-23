(ns sbank.http
  (:import [java.time Duration]
           [com.twitter.finagle Http Service]
           [com.twitter.util Await Awaitable Future]
           [com.twitter.finagle.http Response]
           [org.apache.kafka.clients.consumer KafkaConsumer]
           [org.apache.kafka.clients.producer ProducerRecord])
  (:require [sbank.core :as core]
            [clojure.spec.alpha :as s]))

(defn make-statement-service
  [data-parser]
  (proxy [Service] []
    (apply [request]
      (with-open [consumer (KafkaConsumer. core/consumer-properties)]
        (.subscribe consumer ["read-statement"])
        (let [response (Response/apply)
              statement-order (data-parser (.getContentString request))]
          (println "Statement Order" statement-order)
          (.send core/producer (ProducerRecord. "bank-statement" (core/json-serialize statement-order)))
          (let [statement-value (loop [records []]
                                  (let [record (filter #(= (str statement-order) (.key %)) records)]
                                    (println record)
                                    (if (empty? record)
                                      (recur (seq (.poll consumer (Duration/ofSeconds 1))))
                                      (data-parser (.value (first record))))))]
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
                   (.send core/producer (ProducerRecord. "bank-deposit" (core/json-serialize deposit-order))))
           "Failed! Bad Request."))
        (Future/value response)))))

(defn make-withdrawn-service
  [data-parser]
  (proxy [Service] []
    (apply [request]
      (let [response (Response/apply)
            withdrawn-order (data-parser (.getContentString request))]
        (println "Withdrawn Order" withdrawn-order)
        (println "Withdrawn Order STR" (core/json-serialize withdrawn-order))
        (.setContentString
         response
         (if (s/valid? :bank/withdrawn withdrawn-order)
           (format "Success Withdrawn! New value %s."
                   (.send core/producer (ProducerRecord. "bank-withdrawn" (core/json-serialize withdrawn-order))))
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
        deposit-service (serve-service deposit-port (make-deposit-service core/json-parser))
        withdrawn-service (serve-service withdrawn-port (make-withdrawn-service core/json-parser))
        statement-service (serve-service statement-port (make-statement-service core/json-parser))]
    (Await/all
     (into-array Awaitable [deposit-service withdrawn-service statement-service]))))
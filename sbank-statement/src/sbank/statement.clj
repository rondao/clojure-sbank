(ns sbank.statement
  (:import  [java.time Duration]
            [org.apache.kafka.clients.consumer KafkaConsumer]
            [org.apache.kafka.clients.producer ProducerRecord])
  (:require [sbank.core :as core]
            [clojure.spec.alpha :as s]))

(defn statement! [topic]
  (with-open [consumer (KafkaConsumer. core/consumer-properties)]
    (println "Subscribing to topic: " topic)
    (.subscribe consumer [topic])
    (loop [records []]
      (doseq [value (map #(core/json-parser (.value %)) records)]
        (println "Value" value)
        (when (s/valid? :bank/statement value)
          (.send core/producer (ProducerRecord. "db-statement" (core/json-serialize value)))))
      (recur (seq (.poll consumer (Duration/ofSeconds 1)))))))

(defn -main [& _args]
  (statement! "bank-statement"))
(ns sbank.deposit
  (:import  [java.time Duration]
            [org.apache.kafka.clients.consumer ConsumerConfig KafkaConsumer]
            [org.apache.kafka.clients.producer ProducerConfig ProducerRecord KafkaProducer])
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]))

(s/def :bank/account pos-int?)
(s/def :bank/amount pos-int?)

(s/def :bank/data (s/keys :req [:bank/account :bank/amount]))
(s/def :bank/deposit :bank/data)
(s/def :bank/withdrawn :bank/data)

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

(defn consumer! [topic]
  (with-open [consumer (KafkaConsumer. consumer-properties)]
    (println "Subscribing to topic: " topic)
    (.subscribe consumer [topic])
    (loop [records []]
      (doseq [value (map #(json-parser (.value %)) records)]
        (println "Value" value)
        (when (s/valid? :bank/withdrawn value)
          (.send producer (ProducerRecord. "db-deposit" (json-serialize value)))))
      (recur (seq (.poll consumer (Duration/ofSeconds 1)))))))

(defn -main [& _args]
  (consumer! "bank-deposit"))
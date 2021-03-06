(ns sbank.db
  (:import  [java.time Duration]
            [org.apache.kafka.clients.consumer KafkaConsumer]
            [org.apache.kafka.clients.producer ProducerRecord])
  (:require [sbank.core :as core]
            [clojure.spec.alpha :as s]
            [datomic.client.api :as d]))

(def bank-schema
  [{:db/ident :bank/account
    :db/unique :db.unique/identity
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "The account id"}
   {:db/ident :bank/amount
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "The amount stored in this account"}])

(def bank-client (d/client {:server-type :dev-local
                             :system "dev"}))

(d/create-database bank-client {:db-name "bank"})

(def bank-conn (d/connect bank-client {:db-name "bank"}))

(d/transact bank-conn {:tx-data bank-schema})

(defn bank-read-account
  [account]
  (d/pull
   (d/db bank-conn)
   [:bank/amount]
   [:bank/account account]))

(defn -bank-transfer-data!
  [data]
  (d/transact bank-conn
              {:tx-data [data]}))

(defn -operate-bank-amount!
  [operation order]
  (let [{account :bank/account
         amount :bank/amount} order
        cur-amount (bank-read-account account)
        new-amount (operation
                    (:bank/amount cur-amount)
                    amount)]
    (-bank-transfer-data!
     {:bank/account account
      :bank/amount new-amount})
    new-amount))

(def bank-withdrawn!
  (partial -operate-bank-amount! #(- (or %1 0) %2)))

(def bank-deposit!
  (partial -operate-bank-amount! #(+ (or %1 0) %2)))

(defn bank-statement!
  [producer statement-order]
  (let [account statement-order
        value (bank-read-account account)]
    (println "Send" account " " value)
    (.send producer (ProducerRecord. "read-statement" (str account) (core/json-serialize value)))))

(defn extract-value-and-topic [record]
  (list (core/json-parser
         (.value record))
        (.topic record)))

(defn consumer! []
  (with-open [consumer (KafkaConsumer. core/consumer-properties)]
    (.subscribe consumer ["db-deposit" "db-withdrawn" "db-statement"])
    (let [producer (core/create-producer)]
      (loop [records []]
        (doseq [[value topic] (map extract-value-and-topic records)]
          (println "Value" value "Topic" topic)
          (case topic
            "db-withdrawn" (when (s/valid? :bank/withdrawn value)
                             (bank-withdrawn! value))
            "db-deposit" (when (s/valid? :bank/deposit value)
                           (bank-deposit! value))
            "db-statement" (when (s/valid? :bank/statement value)
                             (bank-statement! producer value))))
        (recur (seq (.poll consumer (Duration/ofSeconds 1))))))))

(defn -main [& _args]
  (consumer!))
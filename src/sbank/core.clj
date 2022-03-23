(ns sbank.core
  (:import [com.twitter.finagle Http Service]
           [com.twitter.util Future]
           [com.twitter.finagle.http Response])
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [datomic.client.api :as d]))

(s/def :bank/account pos-int?)
(s/def :bank/amount pos-int?)

(s/def :bank/data (s/keys :req [:bank/account :bank/amount]))
(s/def :bank/deposit :bank/data)
(s/def :bank/withdrawn :bank/data)

(def bank-client (d/client {:server-type :dev-local
                             :system "dev"}))

(def bank-conn (d/connect bank-client {:db-name "bank"}))

(defn json-parser
  [json-str]
  (try (json/read-str json-str :key-fn keyword)
       (catch Exception e {})))

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

(def bank-deposit!
  (partial -operate-bank-amount! #(+ (or %1 0) %2)))

(def bank-withdrawn!
  (partial -operate-bank-amount! #(- (or %1 0) %2)))

(defn make-deposit-service
  [data-parser]
  (proxy [Service] []
    (apply [request]
      (let [response (Response/apply)
            deposit-order (data-parser (.getContentString request))]   
        (.setContentString
         response
         (if (s/valid? :bank/deposit deposit-order)
           (format "Success Deposit! New value %s."
                   (bank-deposit! deposit-order))
           "Failed! Bad Request."))
        (Future/value response)))))

(defn make-withdrawn-service
  [data-parser]
  (proxy [Service] []
    (apply [request]
      (let [response (Response/apply)
            withdrawn-order (data-parser (.getContentString request))]
        (.setContentString
         response
         (if (s/valid? :bank/withdrawn withdrawn-order)
           (format "Success Withdrawn! New value %s."
                   (bank-withdrawn! withdrawn-order))
           "Failed! Bad Request."))
        (Future/value response)))))

(defn serve-deposit-service
  []
  (Http/serve ":20000" (make-deposit-service json-parser)))

(defn serve-withdraw-service
  []
  (Http/serve ":20001" (make-withdrawn-service json-parser)))
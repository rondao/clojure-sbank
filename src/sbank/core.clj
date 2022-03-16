(ns sbank.core
  (:import [com.twitter.finagle Http Service]
           [com.twitter.util Future]
           [com.twitter.finagle.http Response])
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]))

(s/def :bank/account pos-int?)
(s/def :bank/amount pos-int?)

(s/def :bank/deposit (s/keys :req [:bank/account :bank/amount]))
(s/def :bank/withdrawn (s/keys :req [:bank/account :bank/amount]))

(def bank (atom {}))

(defn make-json-parser
  []
  (fn [json-str]
    (try (json/read-str json-str :key-fn keyword)
         (catch Exception e {}))))

(defn operate-bank-amount!
  [operation order]
  (swap! bank
         update
         (:bank/account order)
         operation (:bank/amount order)))

(def bank-deposit!
  (partial operate-bank-amount! #(+ (or %1 0) %2)))

(def bank-withdrawn!
  (partial operate-bank-amount! #(- (or %1 0) %2)))

(defn make-deposit-service
  [data-parser]
  (proxy [Service] []
    (apply [request]
      (let [response (Response/apply)
            deposit-order (data-parser (.getContentString request))
            account-number (:bank/account deposit-order)]   
        (.setContentString
         response
         (if (s/valid? :bank/deposit deposit-order)
           (format "Success Deposit! New value %s."
                   (get @(bank-deposit! deposit-order)
                        account-number))
           "Failed! Bad Request."))
        (Future/value response)))))

(defn make-withdrawn-service
  [data-parser]
  (proxy [Service] []
    (apply [request]
      (let [response (Response/apply)
            withdrawn-order (data-parser (.getContentString request))
            account-number (:bank/account withdrawn-order)]
        (.setContentString
         response
         (if (s/valid? :bank/withdrawn withdrawn-order)
           (format "Success Withdrawn! New value %s."
                   (get @(bank-withdrawn! withdrawn-order)
                        account-number))
           "Failed! Bad Request."))
        (Future/value response)))))

(defn serve-deposit-service
  []
  (Http/serve ":20000" (make-deposit-service (make-json-parser))))

(defn serve-withdraw-service
  []
  (Http/serve ":20001" (make-withdrawn-service (make-json-parser))))

(serve-deposit-service)
(serve-withdraw-service)
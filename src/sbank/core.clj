(ns sbank.core
  (:import [com.twitter.finagle Http Service]
           [com.twitter.util Await Future]
           [com.twitter.finagle.http Response])
  (:require [clojure.data.json :as json]))

(def bank (atom {}))

(defn make-json-parser
  []
  (fn [json-str]
    (try (json/read-str json-str :key-fn keyword)
         (catch Exception e {}))))

(defn make-deposit-service
  [data-parser]
  (proxy [Service] []
    (apply [request]
      (let [response (Response/apply)
            deposit-order (data-parser (.getContentString request))]
        (swap! bank
               update
               (:id deposit-order)
               #(+ (or % 0) (:value deposit-order)))
        (.setContentString
         response
         (str (get @bank (:id deposit-order))))
        (Future/value response)))))

(defn serve-deposit-service
  []
  (Http/serve ":8080" (make-deposit-service (make-json-parser))))

(Await/serve (serve-deposit-service))
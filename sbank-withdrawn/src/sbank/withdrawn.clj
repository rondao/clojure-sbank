(ns sbank.withdrawn
  (:require [sbank.core :as core]))

(defn -main [& _args]
  (sbank.core/consumer! "bank-withdrawn" "db-withdrawn" :bank/withdrawn))
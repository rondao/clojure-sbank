(ns sbank.deposit
  (:require [sbank.core :as core]))

(defn -main [& _args]
  (core/consumer! "bank-deposit" "db-deposit" :bank/deposit))
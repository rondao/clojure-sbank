(ns sbank.statement
  (:require [sbank.core :as core]))

(defn -main [& _args]
  (core/consumer! "bank-statement" "db-statement" :bank/statement))
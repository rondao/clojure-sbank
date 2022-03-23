(ns sbank.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [datomic.client.api :as d]
            [sbank.core :refer :all]))

(def mock-bank (atom {}))

(defn db-mock [_]
  mock-bank)

(defn pull-mock [_ _ variables]
  (let [account (get variables
                     (inc (.indexOf variables :bank/account)))]
    {:bank/amount (get @mock-bank account)}))

(defn transact-mock [_ tx-data]
  (let [data (first (:tx-data tx-data))
        account (:bank/account data)
        amount (:bank/amount data)]
    (swap! mock-bank assoc account amount)))

(defn mock-ficture [f]
  (with-redefs [d/db db-mock
                d/pull pull-mock
                d/transact transact-mock]
    (f)))

(use-fixtures :each mock-ficture)

(deftest read-from-bank
  (testing "Read amount from account."
    (let [data (gen/generate (s/gen :bank/data))
          account (:bank/account data)
          amount (:bank/amount data)]
      (swap! mock-bank assoc account amount)
      (is (= amount
             (:bank/amount
              (bank-read-account account)))))))

(deftest deposit-from-bank
  (testing "Deposit value to a bank account"
    (let [data (gen/generate (s/gen :bank/data))
          deposit-amount (rand-int 1000)
          account (:bank/account data)
          amount (:bank/amount data)]
      (bank-deposit! data)
      (bank-deposit! {:bank/account account
                      :bank/amount deposit-amount})
      (is (= (+ amount deposit-amount)
             (get @mock-bank account))))))

(deftest withdrawn-from-bank
  (testing "Withdrawn value from a bank account"
    (let [data (gen/generate (s/gen :bank/data))
          withdrawn-amount (rand-int 1000)
          account (:bank/account data)
          amount (:bank/account data)]
      (swap! mock-bank assoc account amount)
      (bank-withdrawn! {:bank/account account
                        :bank/amount withdrawn-amount})
      (is (= (- amount withdrawn-amount)
             (get @mock-bank account))))))
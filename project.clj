(defproject sbank "0.2.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [[lein-sub "0.3.0"]]
  :sub ["sbank-core" "sbank-http" "sbank-deposit" "sbank-withdrawn" "sbank-db"]
  :dependencies [])
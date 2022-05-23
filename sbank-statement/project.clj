(defproject sbank-statement "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :main sbank.statement
  :java-source-paths ["src/java"]
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/test.check "0.9.0"]
                 [org.apache.kafka/kafka-clients "3.1.0"]
                 [com.datomic/dev-local "1.0.242"]
                 [com.twitter/finagle-http_2.13 "22.2.0"]]
  :repositories [["cognitect-dev-tools" {:url      "https://dev-tools.cognitect.com/maven/releases/"
                                         :username :env
                                         :password :env}]]
  :repl-options {:init-ns sbank.statement})

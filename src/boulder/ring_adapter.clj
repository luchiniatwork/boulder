(ns boulder.ring-adapter
  (:require [jsonista.core :as json]))

(defn create-simple-handler
  ([agent-fetcher]
   (create-simple-handler agent-fetcher (constantly nil)))
  ([agent-fetcher agent-name-finder]
   (fn [{:keys [request-method body-params query-params headers]
         :as request}]
     (if (and (= "application/json" (get headers "accept"))
              (= :post request-method))
       (let [agent (or (agent-name-finder request)
                       (:agent query-params))]
         (if-let [agent-proper (agent-fetcher agent)]
           (try
             {:status 200
              :headers {"Content-Type" "application/json"}
              :body (-> body-params agent-proper json/write-value-as-string)}
             (catch Throwable ex
               {:status 500
                :headers {"Content-Type" "text/html"}
                :body (.getMessage ex)}))
           {:status 404
            :headers {"Content-Type" "text/html"}
            :body "not found"}))
       {:status 400
        :headers {"Content-Type" "text/html"}
        :body "Only POST and application/json as an accept header are accepted"}))))

(comment
  (let [agents {"a" identity
                "b" (fn [{:keys [x]}] {:x-plus (+ 5 x)})}
        handler (create-simple-handler (fn [i] (get agents i)))]
    (handler {:request-method :post
              :headers {"accept" "application/json"}
              :query-params {:agent "b"}
              :body-params {:foo :bar
                            :x 6}})))

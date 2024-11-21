(ns boulder.ring-adapter
  (:require [jsonista.core :as json]))

(defn create-simple-handler
  "`agent-fetcher` is a f that returns a f that will be called with the
  extracted request parameters.

  `opts` takes:

  * `:agent-name-finder`: a f that, given the request, returns the
  name of the agent. Default behavior if `nil` is to grab the `agent`
  parameter from `:query-params`

  * `:params-extractor`: a f that, given the request, returns the
  parameters to be sent to the agent.
  "
  ([agent-fetcher]
   (create-simple-handler agent-fetcher nil))
  ([agent-fetcher {:keys [agent-name-finder params-extractor]
                   :or {agent-name-finder #(-> % :query-params :agent)
                        params-extractor #(-> % :body-params)}
                   :as _opts}]
   (fn [{:keys [request-method headers]
         :as request}]
     (if (and (= "application/json" (get headers "accept"))
              (= :post request-method))
       (let [agent (agent-name-finder request)]
         (if-let [agent-proper (agent-fetcher agent)]
           (try
             {:status 200
              :headers {"Content-Type" "application/json"}
              :body (-> request
                        params-extractor
                        agent-proper
                        json/write-value-as-string)}
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

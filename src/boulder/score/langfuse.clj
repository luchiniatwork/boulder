(ns boulder.score.langfuse
  (:require [boulder.score.core :as core]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clj-http.client :as http]
            [jsonista.core :as json]))

(defn ^:private normalize-data-type [dt]
  (case dt
    :numeric
    "NUMERIC"
    :boolean
    "BOOLEAN"
    :categorical
    "CATEGORICAL"))


(defn ^:private normalize-payload
  [payload]
  (let [{:keys [observation-id comment data-type]} (->kebab-case-keyword payload)]
    (cond-> {:traceId (:trace-id payload)
             :name (:name payload)
             :value (:value payload)}
      observation-id
      (assoc :observationId observation-id)
      comment
      (assoc :comment comment)
      data-type
      (assoc :dataType (normalize-data-type data-type)))))


(defn create [{:keys [url public-key secret-key] :as _server-config}]
  (reify core/IScore
    (post [_ payload]
      (let [payload' (normalize-payload payload)
            {:keys [status body]} (-> (str url "/api/public/scores")
                                      (http/post {:content-type :json
                                                  :accept :json
                                                  :basic-auth [public-key secret-key]
                                                  :body (json/write-value-as-string payload')}))]
        (when (= 200 status)
          (-> body
              (json/read-value json/keyword-keys-object-mapper)
              :id))))))

(ns boulder.score.langfuse
  (:require [boulder.score.core :as core]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clj-http.client :as http]
            [jsonista.core :as json]
            [taoensso.timbre :refer [debug info warn error fatal report]]))

(defn ^:private normalize-data-type [dt]
  (case dt
    :numeric
    "NUMERIC"
    :boolean
    "BOOLEAN"
    :categorical
    "CATEGORICAL"))


(defn ^:private normalize-value [{:keys [data-type value]}]
  (case data-type
    :boolean
    (if value 1 0)
    value))


(defn ^:private normalize-payload
  [payload]
  (let [{:keys [observation-id comment data-type]} (transform-keys ->kebab-case-keyword payload)]
    (cond-> {:traceId (:trace-id payload)
             :name (:name payload)
             :value (normalize-value payload)}
      observation-id
      (assoc :observationId observation-id)
      comment
      (assoc :comment comment)
      data-type
      (assoc :dataType (normalize-data-type data-type)))))


(defn create [{:keys [url public-key secret-key] :as _server-config}]
  (reify core/IScore
    (post [_ payload]
      (debug {:msg "Posting score to Langfuse"
              :payload payload})
      (try
        (let [payload' (normalize-payload payload)
              {:keys [status body]} (-> (str url "/api/public/scores")
                                        (http/post {:content-type :json
                                                    :accept :json
                                                    :basic-auth [public-key secret-key]
                                                    :body (json/write-value-as-string payload')}))]
          (if (= 200 status)
            (do (debug {:msg "Successfully posted score to Langfuse"
                        :normalized-payload payload'})
                (-> body
                    (json/read-value json/keyword-keys-object-mapper)
                    :id))
            (fatal {:msg "Failed to post score to Langfuse"
                    :normalized-payload payload'})))
        (catch Throwable ex
          (fatal {:msg "Exception posting score to Langfuse"
                  :ex ex})
          (throw ex))))))

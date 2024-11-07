(ns boulder.node-factory.langfuse-score
  (:require [boulder.score.core :as score]
            [boulder.score.langfuse :as langfuse-score]))


(defn create-node
  ([langfuse-server-config]
   (create-node langfuse-server-config))
  ([langfuse-server-config metadata]
   (let [node-score (langfuse-score/create langfuse-server-config)]
     (fn [input-map]
       (score/post node-score
                   (merge metadata input-map))))))

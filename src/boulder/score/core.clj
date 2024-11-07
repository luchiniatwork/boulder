(ns boulder.score.core)

(def data-types #{:numeric :boolean :categorical})

(defprotocol IScore
  (post [_ payload]))

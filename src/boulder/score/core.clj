(ns boulder.score.core)

(defprotocol IScore
  (post [_ payload]))

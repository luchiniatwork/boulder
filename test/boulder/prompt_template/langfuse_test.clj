(ns boulder.prompt-template.langfuse-test
  (:require [boulder.prompt-template.core :as prompt-core]
            [boulder.prompt-template.langfuse :as prompt-langfuse]
            [clojure.test :refer [testing is deftest use-fixtures]]
            [jsonista.core :as json]
            [org.httpkit.server :as http-server]
            [reitit.ring :as ring]
            [ring.middleware.authorization :refer [wrap-authorization]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]])
  (:import [java.util Base64]))

(def access-counter (atom 0))

(def prompt-once-work? (atom true))

(def ^:private handler
  (ring/ring-handler
   (ring/router
    [["/api/public/v2/prompts/prompt-completion"
      {:get (fn [request]
              (swap! access-counter inc)
              {:status  200
               :headers {"Content-Type" "text/html"}
               :body    (json/write-value-as-string {:prompt "foo is {{foo}}"
                                                     :config {:foo "{{foo}}"}})})}]
     ["/api/public/v2/prompts/prompt-chat"
      {:get (fn [request]
              (swap! access-counter inc)
              {:status  200
               :headers {"Content-Type" "text/html"}
               :body    (json/write-value-as-string {:prompt [{:role "user"
                                                               :content "foo is {{foo}}"}]
                                                     :config {:foo "{{foo}}"}})})}]
     ["/api/public/v2/prompts/prompt-work-once"
      {:get (fn [request]
              (if @prompt-once-work?
                (do (reset! prompt-once-work? false)
                    (swap! access-counter inc)
                    {:status  200
                     :headers {"Content-Type" "text/html"}
                     :body    (json/write-value-as-string {:prompt "foo is {{foo}}"
                                                           :config {:foo "{{foo}}"}})})
                {:status 500}))}]])))

(def ^:private server-config {:url "http://localhost:3404"
                              :public-key "public"
                              :secret-key "secret"})


(defn ^:private wrap-authorized-access
  [handler]
  (fn [{:keys [authorization] :as request}]
    (let [credentials "public:secret"
          encoded-credentials (.encodeToString (Base64/getEncoder) (.getBytes credentials "UTF-8"))]
      (when (not= encoded-credentials (:token authorization))
        (throw (ex-info "Invalid credentials" {})))
      (handler request))))


(defn ^:private server-fixture [f]
  (let [server (http-server/run-server
                (-> handler
                    wrap-authorized-access
                    wrap-authorization
                    (wrap-defaults (assoc api-defaults :static "public")))
                {:port 3404})]
    (reset! access-counter 0)
    (reset! prompt-once-work? true)
    (f)
    (server)))


(use-fixtures :each server-fixture)


(deftest basic-applies
  (testing "basic completion prompt"
    (let [prompt (prompt-langfuse/create server-config
                                         "prompt-completion")]
      (is (= {:prompt "foo is x"
              :config {:foo "{{foo}}"}}
             (prompt-core/apply prompt {:foo "x"}))))
    (let [prompt (prompt-langfuse/create server-config
                                         "prompt-completion"
                                         {:keys-to-apply [:prompt :config]})]
      (is (= {:prompt "foo is x"
              :config {:foo "x"}}
             (prompt-core/apply prompt {:foo "x"})))))

  (testing "basic chat prompt"
    (let [prompt (prompt-langfuse/create server-config
                                         "prompt-chat")]
      (is (= {:prompt [{:role "user" :content "foo is x"}]
              :config {:foo "{{foo}}"}}
             (prompt-core/apply prompt {:foo "x"}))))
    (let [prompt (prompt-langfuse/create server-config
                                         "prompt-chat"
                                         {:keys-to-apply [:prompt :config]})]
      (is (= {:prompt [{:role "user" :content "foo is x"}]
              :config {:foo "x"}}
             (prompt-core/apply prompt {:foo "x"}))))))


(deftest lazy-init
  (testing "default is non-lazy"
    (prompt-langfuse/create server-config "prompt-completion")
    (is (= 1 @access-counter)))
  (testing "lazy should not fetch until needed"
    (let [prompt (prompt-langfuse/create server-config
                                         "prompt-completion"
                                         {:lazy-init? true})]
      (is (= 1 @access-counter))
      (prompt-core/apply prompt {})
      (is (= 2 @access-counter)))))


(deftest cache
  (testing "cache works by default"
    (let [prompt (prompt-langfuse/create server-config
                                         "prompt-completion")]
      (dotimes [_ 50] (prompt-core/apply prompt {}))
      (is (= 1 @access-counter))))

  (testing "cache expires"
    (let [prompt (prompt-langfuse/create server-config
                                         "prompt-completion"
                                         {:cache-time-ms 150})]
      (dotimes [_ 50] (prompt-core/apply prompt {}))
      (is (= 2 @access-counter))
      (Thread/sleep 250)
      (dotimes [_ 50] (prompt-core/apply prompt {}))
      (is (= 3 @access-counter))))

  (testing "cache turned off"
    (let [prompt (prompt-langfuse/create server-config
                                         "prompt-completion"
                                         {:cache-time-ms 0})]
      (dotimes [_ 10] (prompt-core/apply prompt {}))
      (is (> 13 @access-counter)))))


(deftest continue-on-failed
  (let [prompt (prompt-langfuse/create server-config
                                       "prompt-work-once"
                                       {:cache-time-ms 0})]
    (dotimes [_ 10] (prompt-core/apply prompt {}))
    (is (= 1 @access-counter))))


(deftest fail-fetch
  (is (thrown? clojure.lang.ExceptionInfo
               (prompt-langfuse/create server-config
                                       "not-found"))))

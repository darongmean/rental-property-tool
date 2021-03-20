(ns darongmean.property.openrefine
  "Openrefine reconcile service"
  (:require
   [aleph.http :as http]
   [clojure-csv.core :as csv]
   [clojure.string :as str]
   [darongmean.property.openrefine.similarity :as similarity]
   [jsonista.core :as json]
   [muuntaja.core :as muuntaja]
   [reitit.ring :as ring]
   [reitit.ring.middleware.muuntaja :as middleware.muuntaja]
   [ring.middleware.keyword-params :as middleware.keyword-params]
   [ring.middleware.params :as middleware.params]))


(def ^:dynamic *reconcile-server*)


(def db
  (atom {:metadata [{:id "admin-level-01" :name "admin-level-01"}
                    {:id "admin-level-02" :name "admin-level-02"}
                    {:id "admin-level-03" :name "admin-level-03"}
                    {:id "admin-level-04" :name "admin-level-04"}]
         :data {"admin-level-01" ["phnom penh" "phnom penh"]
                "admin-level-02" ["russey keo" "russey keo"]
                "admin-level-03" ["russey keo" "russey keo"]
                "admin-level-04" ["mitta pheap" "mitta pheap"]}}))


(defn handler
  [_]
  {:status 200, :body {:ok 123}})


(defn log-request
  [args]
  (println (:uri args) "request:" (:params args))
  (println (:uri args) "response:" {:status 404 :body "" :headers []})
  {:status 404 :body "" :headers []})


(defn query-result
  [{:keys [data] :as _db} {:strs [query limit type] :as _query-item}]
  (let [data-coll (into #{} (get data type))
        score-coll (map #(similarity/jaro-winkler (str/lower-case query) (str/lower-case %)) data-coll)]
    (->> (map (fn [matched-item score]
                {:name matched-item
                 :score score
                 :match (== 1.0 score)})
              data-coll
              score-coll)
         (map-indexed (fn [idx match]
                        (assoc match :id idx)))
         (filter #(->> % :score (< 0)))
         (sort-by :score >)
         (take (or limit 3)))))


(defn query-batch-result
  [db query-batch]
  (->> query-batch
       (map (fn [[query-key query-val]]
              [query-key {:result (query-result db query-val)}]))
       (into {})))


(defn service-manifest
  [{:keys [metadata] :as _db}]
  {:name "local-reconcile"
   :identifierSpace "http://localhost:3000/doc/#indentifier-space"
   :schemaSpace "http://localhost:3000/doc/#schema-spec"
   :defaultTypes metadata})


(defn reconcile
  [db {{:keys [callback queries]} :params}]
  (let [callback-response #(str callback "(" (json/write-value-as-string %) ");")
        response (if queries
                   (query-batch-result db (json/read-value queries))
                   (service-manifest db))]
    (cond-> response
      callback (callback-response))))


(defn response-fn
  [f]
  (fn [request]
    (println (:uri request) "request:" (:params request))
    (let [response (f @db request)]
      (println (:uri request) "response:" response)
      {:status 200 :body response})))


(def reconcile-handler
  (-> [["/ping" {:get handler}]
       ["/reconcile" {:get (response-fn reconcile)
                      :post (response-fn reconcile)}]]
      (ring/router {:data {:muuntaja muuntaja/instance
                           :middleware [middleware.params/wrap-params
                                        middleware.keyword-params/wrap-keyword-params
                                        middleware.muuntaja/format-middleware]}})
      (ring/ring-handler (ring/create-default-handler
                          {:not-found log-request
                           :method-not-allowed log-request
                           :not-acceptable log-request}))))


(defn start
  []
  (let [server (http/start-server #'reconcile-handler
                                  {:port 3000})]
    (alter-var-root #'*reconcile-server* (constantly server))
    (println "Server started on port 3000")))


(defn stop
  []
  (when *reconcile-server*
    (.close *reconcile-server*)))


(defn remove-comment
  [row-coll]
  (remove (fn [[column-01 & _]] (re-find #"^#" column-01)) row-coll))


(defn new-db
  [row-coll]
  (let [[headers & body] row-coll
        metadata (fn [column-name]
                   {:id column-name :name column-name})
        data-by-index (fn [idx row]
                        (nth row idx nil))
        data-by-column (fn [idx column-name]
                         {column-name (map #(data-by-index idx %) body)})]
    {:metadata (map metadata headers)
     :data (->> headers
                (map-indexed data-by-column)
                (apply merge))}))


(defn run
  [{:keys [csv]}]
  (try
    (reset! db (new-db (-> (str csv)
                           (slurp)
                           (csv/parse-csv)
                           (remove-comment))))
    (start)
    (catch Throwable ex
      (println ex))))


(comment
  ;; restart server
  (start)
  (stop)
  (do (stop)
      (start)))

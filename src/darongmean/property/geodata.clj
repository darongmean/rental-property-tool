(ns darongmean.property.geodata
  "Read Geodata from yaml files and write to CSV in current folder"
  (:require
   [clj-yaml.core :as yaml]
   [clojure-csv.core :as csv]))


(defn read-region-name-by-code
  [file-name]
  (let [geo-data (-> file-name
                     (slurp)
                     (yaml/parse-string :keywords false)
                     (first)
                     (second))]
    (->> geo-data
         (map (fn [[k v]] [k (get-in v ["name" "latin"])]))
         (into {}))))


(defn regions-table
  [{:keys [provinces districts communes villages]}]
  (let [header ["code" "province" "district" "commune" "village"]
        body (for [[village-code village-name] villages
                   :let [commune-code (subs village-code 0 6)
                         district-code (subs village-code 0 4)
                         province-code (subs village-code 0 2)]]
               [village-code
                (get provinces province-code)
                (get districts district-code)
                (get communes commune-code)
                village-name])]
    (cons header (sort-by first body))))


(defn write-csv
  [regions-table file-name]
  (->> regions-table
       (csv/write-csv)
       (spit file-name)))


(defn run
  [_args]
  (try
    (println "Reading data from admin-region-cambodia/ ...")
    (-> {:provinces (read-region-name-by-code "admin-region-cambodia/provinces.yml")
         :districts (read-region-name-by-code "admin-region-cambodia/districts.yml")
         :communes (read-region-name-by-code "admin-region-cambodia/communes.yml")
         :villages (read-region-name-by-code "admin-region-cambodia/villages.yml")}
        (regions-table)
        (write-csv "geodata-cambodia.csv"))
    (println "Geodata was written to geodata_cambodia.csv")
    (catch Exception ex
      (println ex))))

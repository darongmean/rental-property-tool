(ns darongmean.property.openrefine.similarity
  (:import
   info.debatty.java.stringsimilarity.JaroWinkler))


(def ^:private jaro-winkler-instance (JaroWinkler.))


(defn jaro-winkler
  [s1 s2]
  (.similarity jaro-winkler-instance s1 s2))

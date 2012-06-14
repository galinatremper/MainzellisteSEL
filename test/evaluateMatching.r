library(data.table)

evaluateMatching <- function()
{
  weightsReduced <- data.table(read.table("weights.txt", sep=";", header=TRUE))
  
  # Optimale Klassifikation
  weightsUnique <- sort(unique(weightsReduced$weight))
  nMisclass <- sapply(weightsUnique, function(w) 
    weightsReduced[weight >= w, sum(is_match=="false")] + 
    weightsReduced[weight < w, sum(is_match=="true")])
    
  optThreshold <- weightsUnique[which.min(nMisclass)]
  
  cat(sprintf("Optimaler Threshold: %g\n", optThreshold))
  cat("Optimale Klassifikation:\n")
  print(weightsReduced[,table(is_match, weight >= optThreshold)])

  #
  # �berlappungsbereich
  maxWNonMatch <- weightsReduced[is_match=="false", max(weight)]
  minWMatch <- weightsReduced[is_match=="true", min(weight)]
  nOverlap <- weightsReduced[weight >= minWMatch & weight <= maxWNonMatch, length(weight)]
  
  cat(sprintf("Der �berlappungsbereich reicht von %g bis %g und enth�lt %d Datensatzpaare\n", minWMatch, maxWNonMatch, nOverlap))
}
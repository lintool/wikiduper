
nReducers=20
lang=en
# input = $1
# output $2
# nHash $3
# bits $4
# k $5
# n $6
# shingleLen $7

etc/hadoop-cluster.sh courseproj.application.MinhashWikipediaPages -wiki_language $lang -input $1 -output enwiki-20130503-pairs -numReducers $nReducers -nHash $3 -k $5 -n $6 -bits $4 -shingleLen $7
etc/hadoop-cluster.sh courseproj.application.DedupSentencePairs -input enwiki-20130503-pairs -output enwiki-20130503-pairsdedup
rm -rf enwiki-20130503-pairsdedup
hadoop fs -get enwiki-20130503-pairsdedup
hadoop fs -rm -r ewiki-20130503-clusters
etc/run.sh courseproj.application.MergeClusters -pairfile enwiki-20130503-pairsdedup -output enwiki-20130503-clusters
hadoop fs -put ewiki-20130503-clusters
etc/hadoop-cluster.sh courseproj.application.GetSentenceClusters -input $1 -wiki_language en -clustermap enwiki-20130503-clusters -output $2 -numReducers $nReducers
hadoop fs -get $2

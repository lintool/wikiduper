
nReducers=20
lang=en
# input = $1
# output $2
# nHash $3
# bits $4
# k $5
# n $6
# shingleLen $7

echo etc/hadoop-local.sh wikiduper.application.MinhashWikipediaPages -wiki_language $lang -input $1 -output enwiki-20130503-pairs -numReducers $nReducers -nHash $3 -k $5 -n $6 -bits $4 -shingleLen $7
etc/hadoop-local.sh wikiduper.application.MinhashWikipediaPages -wiki_language $lang -input $1 -output enwiki-20130503-pairs -numReducers $nReducers -nHash $3 -k $5 -n $6 -bits $4 -shingleLen $7
echo etc/hadoop-local.sh wikiduper.application.DedupSentencePairs -input enwiki-20130503-pairs -output enwiki-20130503-pairsdedup
etc/hadoop-local.sh wikiduper.application.DedupSentencePairs -input enwiki-20130503-pairs -output enwiki-20130503-pairsdedup
echo rm -rf enwiki-20130503-pairsdedup
rm -rf enwiki-20130503-pairsdedup
echo hadoop fs -get enwiki-20130503-pairsdedup
hadoop fs -get enwiki-20130503-pairsdedup
echo hadoop fs -rm -r enwiki-20130503-clusters
hadoop fs -rm -r enwiki-20130503-clusters
echo etc/run.sh wikiduper.application.MergeClusters -pairfile enwiki-20130503-pairsdedup -output enwiki-20130503-clusters
etc/run.sh wikiduper.application.MergeClusters -pairfile enwiki-20130503-pairsdedup -output enwiki-20130503-clusters
echo hadoop fs -put enwiki-20130503-clusters
hadoop fs -put enwiki-20130503-clusters
echo etc/hadoop-local.sh wikiduper.application.GetSentenceClusters -input $1 -wiki_language en -clustermap enwiki-20130503-clusters -output $2 -numReducers $nReducers
etc/hadoop-local.sh wikiduper.application.GetSentenceClusters -input $1 -wiki_language en -clustermap enwiki-20130503-clusters -output $2 -numReducers $nReducers
echo hadoop fs -get $2 $2-$3-$5-$6-$7-$4
hadoop fs -get $2 $2-$3-$5-$6-$7-$4
#echo mv $2-$3-$5-$6-$7-$4 /scratch0/sew
#mv $2-$3-$5-$6-$7-$4 /scratch0/sew
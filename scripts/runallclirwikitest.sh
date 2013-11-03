#k=12
#bits=30
#nHash=50
#n=80
#nSamples=100

fin=$1
flang=$2
fmapping=${fin}.mapping
fpack=${fin}.pack

ein=$3
elang=$4
emapping=${ein}.mapping
epack=${ein}.pack

#../Ivory/data
ivoryDataDir=$5
samples=$6

f2eprobs=${ivoryDataDir}/ttable.${flang}-${elang}
e2fprobs=${ivoryDataDir}/ttable.${elang}-${flang}
fvocabsrc=${ivoryDataDir}/vocab.${flang}-${elang}.${flang}
fvocabtgt=${ivoryDataDir}/vocab.${elang}-${flang}.${flang}
evocabsrc=${ivoryDataDir}/vocab.${elang}-${flang}.${elang}
evocabtgt=${ivoryDataDir}/vocab.${flang}-${elang}.${elang}
fstopwords=${ivoryDataDir}/token/${flang}.stop
estopwords=${ivoryDataDir}/token/${elang}.stop
ftokens=${ivoryDataDir}/token/${flang}-token.bin
etokens=${ivoryDataDir}/token/${elang}-token.bin

bits=$7
k=$8
n=$9
nhash=${10}

output=${11}
preprocout=${output}.preproc
mhoutput=${output}.mh
mergeoutput=${output}.clusters
sentenceoutput=${output}.${samples}-${bits}-${k}-${n}-${nhash}.sentences

#etc/hadoop-cluster.sh wikiduper.clir.minhashwiki.PreprocessWikiInput -elang ${elang} -ewiki ${epack} -flang ${flang} -fwiki ${fpack} -output ${preprocout}
#echo "etc/hadoop-cluster.sh wikiduper.clir.minhashwiki.MinhashCLIR -bits ${bits} -k ${k} -M ${samples} -n ${n} -nHash ${nhash} -numReducers 20 -output ${output} -fVocabSrc ${fvocabsrc} -fVocabTgt ${fvocabtgt} -eVocabSrc ${evocabsrc} -eVocabTgt ${evocabtgt} -e2fprobs ${e2fprobs} -f2eprobs ${f2eprobs} -fLang ${flang} -eLang ${elang} -fStopWords ${fstopwords} -eStopWords ${estopwords} -fTokens ${ftokens} -eTokens ${etokens} -input ${preprocout}"
#etc/hadoop-cluster.sh wikiduper.clir.minhashwiki.MinhashCLIR -bits ${bits} -k ${k} -M ${samples} -n ${n} -nHash ${nhash} -numReducers 20 -output ${mhoutput} -fVocabSrc ${fvocabsrc} -fVocabTgt ${fvocabtgt} -eVocabSrc ${evocabsrc} -eVocabTgt ${evocabtgt} -e2fprobs ${e2fprobs} -f2eprobs ${f2eprobs} -fLang ${flang} -eLang ${elang} -fStopWords ${fstopwords} -eStopWords ${estopwords} -fTokens ${ftokens} -eTokens ${etokens} -input ${preprocout}
#echo "etc/hadoop-cluster.sh wikiduper.clir.minhashwiki.MergeClusters -input ${mhoutput} -output ${mergeoutput}"
#etc/hadoop-cluster.sh wikiduper.clir.minhashwiki.MergeClusters -input ${mhoutput} -output ${mergeoutput}
#echo "etc/hadoop-cluster.sh wikiduper.clir.minhashwiki.GetSentenceClusters -clustermap ${mergeoutput} -input ${preprocout} -elang ${elang} -flang ${flang} -output ${sentenceoutput}"
#etc/hadoop-cluster.sh wikiduper.clir.minhashwiki.GetSentenceClusters -clustermap ${mergeoutput} -input ${preprocout} -elang ${elang} -flang ${flang} -output ${sentenceoutput}

#echo "etc/hadoop-text.sh ${sentenceoutput} > sentencesout/${sentenceoutput}.txt"
#etc/hadoop-text.sh ${sentenceoutput} > sentencesout/${sentenceoutput}.txt

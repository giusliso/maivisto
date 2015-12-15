package it.maivisto.utility;



import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import ts.TSUtils;
import ts.annotator.StopWordAnnotator;
import ts.annotator.WNFirstSenseAnnotator;
import ts.data.Annotation;
import ts.data.TSDocument;
import ts.evaluation.TSinstance;
import ts.evaluation.impl.Semeval;
import ts.nlp.NlpPipeline;
import ts.similarity.impl.DSMcompositionalitySUM;
import ts.similarity.impl.distributional.VectorStoreRAM;

/**
 *
 * @author annalina
 */
@SuppressWarnings("unused")
public class STS {

    NlpPipeline nlpPipeline = null;
    VectorStoreRAM storeLSA;
    VectorStoreRAM storeRI;
    VectorStoreRAM storeLSARI;
    VectorStoreRAM storePERM;
    VectorStoreRAM storeLSAPERM;
    VectorStoreRAM storeCONV;
    VectorStoreRAM storeLSACONV;
    DSMcompositionalitySUM dsmCompSum;
    WNFirstSenseAnnotator wn;
    StopWordAnnotator sw;
    Properties props;


    /**
     * Initialized all the classes required for computing the similarity between
     * two texts.
     *
     * @param configuration_file - the configuration file, check for an example under config/config.proprierties
     * @param staking_file - the xml configuration file containing information for performing the staking algorithm. See under config/staking_vincente.xml for an example. This file is not required as long as the algorithm is able to retrieve the staking model and filter files under resources.
     * @throws IOException
     * @throws Exception
     */
    public STS(String configuration_file, String staking_file) throws IOException, Exception {

        nlpPipeline = NlpPipeline.getInstance(false, true);
        props = new Properties();
        //was "./config/config.properties"
        props.load(new FileReader(configuration_file));
        wn = new WNFirstSenseAnnotator(props);
        sw = new StopWordAnnotator(props);
        wn.init();
        sw.init();
        Logger.getLogger(STS.class.getName()).log(Level.INFO, "Init DSMs modules ...");
        storeLSA = new VectorStoreRAM();
        ((VectorStoreRAM) storeLSA).initFromFile(props.getProperty("distributional.lsa.store"));
        storeRI = new VectorStoreRAM();
        ((VectorStoreRAM) storeRI).initFromFile(props.getProperty("distributional.ri.store"));
        storeLSARI = new VectorStoreRAM();
        ((VectorStoreRAM) storeLSARI).initFromFile(props.getProperty("distributional.lsari.store"));
        storePERM = new VectorStoreRAM();
        ((VectorStoreRAM) storePERM).initFromFile(props.getProperty("distributional.perm.store"));
        storeLSAPERM = new VectorStoreRAM();
        ((VectorStoreRAM) storeLSAPERM).initFromFile(props.getProperty("distributional.lsaperm.store"));
        storeCONV = new VectorStoreRAM();
        ((VectorStoreRAM) storeCONV).initFromFile(props.getProperty("distributional.conv.store"));
        storeLSACONV = new VectorStoreRAM();
        ((VectorStoreRAM) storeLSACONV).initFromFile(props.getProperty("distributional.lsaconv.store"));
        dsmCompSum = new DSMcompositionalitySUM(props);
    }

    /**
     * Computes a set of similarities but without the stacking score.
     *
     * @param text1
     * @param text2
     * @return
     * @throws Exception
     */
    public TSinstance computeSimilarities(String text1, String text2) throws Exception {

        long start = System.currentTimeMillis();
        TSinstance instance = new TSinstance("1", text1, text2);
        Logger.getLogger(Semeval.class.getName()).log(Level.INFO, "Annotating example: {0}", instance.getId());
        TSDocument doc1 = nlpPipeline.annotate("1", instance.getT1());
        for (int k = 0; k < doc1.getSentences().size(); k++) {
            wn.annotate(doc1.getSentences().get(k));
            sw.annotate(doc1.getSentences().get(k));
        }
        TSDocument doc2 = nlpPipeline.annotate("2", instance.getT2());
        for (int k = 0; k < doc2.getSentences().size(); k++) {
            wn.annotate(doc2.getSentences().get(k));
            sw.annotate(doc2.getSentences().get(k));
        }
        Logger.getLogger(Semeval.class.getName()).log(Level.INFO, "Annotation done in {0}", TSUtils.getTimeString(start, System.currentTimeMillis()));

        int tokenSize1 = doc1.getAllTokens().size();
        int tokenSize2 = doc2.getAllTokens().size();

        start = System.currentTimeMillis();

        Logger.getLogger(Semeval.class.getName()).log(Level.INFO, "DSM compositional similarity (SUM)...");
        dsmCompSum.setVectorStore(storeLSA);
        instance.getFeatureSet().setFeatures("dsmCompSUM-lsa", dsmCompSum.similarity(doc1, doc2, Annotation.LEMMA_TYPE));
        dsmCompSum.setVectorStore(storeRI);
        instance.getFeatureSet().setFeatures("dsmCompSUM-ri", dsmCompSum.similarity(doc1, doc2, Annotation.LEMMA_TYPE));
        dsmCompSum.setVectorStore(storeLSARI);
        instance.getFeatureSet().setFeatures("dsmCompSUM-lsari", dsmCompSum.similarity(doc1, doc2, Annotation.LEMMA_TYPE));
        dsmCompSum.setVectorStore(storePERM);
        instance.getFeatureSet().setFeatures("dsmCompSUM-perm", dsmCompSum.similarity(doc1, doc2, Annotation.LEMMA_TYPE));
        dsmCompSum.setVectorStore(storeLSAPERM);
        instance.getFeatureSet().setFeatures("dsmCompSUM-lsaperm", dsmCompSum.similarity(doc1, doc2, Annotation.LEMMA_TYPE));
        dsmCompSum.setVectorStore(storeCONV);
        instance.getFeatureSet().setFeatures("dsmCompSUM-conv", dsmCompSum.similarity(doc1, doc2, Annotation.LEMMA_TYPE));
        dsmCompSum.setVectorStore(storeLSACONV);
        instance.getFeatureSet().setFeatures("dsmCompSUM-lsaconv", dsmCompSum.similarity(doc1, doc2, Annotation.LEMMA_TYPE));
        Logger.getLogger(Semeval.class.getName()).log(Level.INFO, "DSM compositional similarity (SUM) done in {0}", TSUtils.getTimeString(start, System.currentTimeMillis()));

        instance.setScore(2.5f);
        return instance;
    }



    /**
     * Given the set of similarities computed so far, it builds a weka instance
     * to be used with the staking algorithm on the basis of making the
     * prediction. In order to build the instance, it uses the feature set names
     * in addition to the instance id and score.
     *
     * @param instance
     * @param data
     * @return
     */
    private void makeInstance(BufferedWriter bw, TSinstance instance) throws IOException {
 
        //build the header
        String header = "id\t";
        String values = instance.getId()+"\t";
        
        List<String> names = instance.getFeatureSet().getSortedNames();
        
        for (int k = 0; k<names.size(); k++){
            header += names.get(k)+"\t";
            values += instance.getFeatureSet().getValue(names.get(k))+"\t";
        }
        header+="score";
        values+=instance.getScore();
        bw.write(header);
        bw.newLine();
        bw.write(values);
        bw.close();
    }

    public static void main(String[] args) throws Exception {
        String t1 = "measure the depth of a body of water";
        String t2 = "any large deep body of water.";
        float predicted_value = 3.802371089514663f;
        STS vts = new STS("/home/fanalina/WORKDIR/git/textualsimilarity/config/config.properties", "/home/fanalina/WORKDIR/git/textualsimilarity/experiments/stacking_vincente.xml");
        TSinstance fs = vts.computeSimilarities(t1, t2);
        Iterator<String> it = fs.getFeatureSet().getNamesIterator();
        while (it.hasNext()){
            String feature = it.next();
            System.out.println(feature + " "+ fs.getFeatureSet().getValue(feature));
        }
    }
}

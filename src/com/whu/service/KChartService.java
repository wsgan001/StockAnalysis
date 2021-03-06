package com.whu.service;

import com.whu.algorithms.KChartThread;
import com.whu.entity.ParamWeight;
import com.whu.entity.ResultEntity;
import com.whu.entity.ResultRank;
import com.whu.util.*;

import java.util.*;

/**
 * Date: 20/11/2016
 * Author: qinjiangbo@github.io
 */
public class KChartService {

    /**
     * 通过算法比较图片相似度
     * @param sourceImage 源图片
     * @param type 比较类型
     * @return 最相似图片
     */
    public static ResultEntity compareSimilarity(String sourceImage, CompareType type, Algorithms algorithms) {

        String[] imageNames = FileNameUtil.listImageNames();

        // 判断用户输入的图片信息是否有效
        List<String> nameList = Arrays.asList(imageNames);
        if (!nameList.contains(sourceImage)) {
            return null;
        }

        int size = imageNames.length - 1;
        String[] imagePath = new String[size];

        // 结果实体
        ResultEntity resultEntity = new ResultEntity(size);
        List<KChartThread> threadList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Class clazz = algorithms.getClazz();
            KChartThread thread = null;
            try {
                thread = (KChartThread) clazz.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
            threadList.add(thread);
        }

        int tag = 1;
        for (String targetImage: imageNames) {
            if(!targetImage.equals(sourceImage)) {
                String image0 = ServerConstants.KCHART_IMAGES + sourceImage + "-" + type.getName() + ".jpg";
                String imageI = ServerConstants.KCHART_IMAGES + targetImage + "-" + type.getName() + ".jpg";
                imagePath[tag - 1] = targetImage.substring(0, targetImage.indexOf(".txt"));
                threadList.get(tag - 1).start(tag++, resultEntity.getSimilarity(), image0, imageI);
            }
        }
        for (int i = 0; i < size; i++) {
            while (threadList.get(i).isAlive()) ; // 直到这个线程结束
        }
        resultEntity.setPath(imagePath);
        return resultEntity;
    }

    /**
     * 通过一个算法综合比较图片相似度
     * @param sourceImage 源图片
     * @return
     */
    public static ResultEntity mixSimilarityComparision(String sourceImage, Algorithms algorithms) {
        ResultEntity kResultEntity = null;
        if (ParamWeight.K_WEIGHT != 0.0) {
            kResultEntity = compareSimilarity(sourceImage, CompareType.K, algorithms);
        }
        ResultEntity vResultEntity = null;
        if ((1 - ParamWeight.K_WEIGHT) != 0.0) {
            vResultEntity = compareSimilarity(sourceImage, CompareType.V, algorithms);
        }
        if (kResultEntity == null && vResultEntity == null) {
            return null;
        }
        int size = kResultEntity == null ? vResultEntity.getSize() : kResultEntity.getSize();
        ResultEntity resultEntity = new ResultEntity(size);
        String[] path = kResultEntity == null ? vResultEntity.getPath() : kResultEntity.getPath();
        resultEntity.setPath(path);
        double[] similarity = new double[size];
        double[] kSimilarity = kResultEntity == null ? new double[size] : kResultEntity.getSimilarity();
        double[] vSimilarity = vResultEntity == null ? new double[size] : vResultEntity.getSimilarity();
        for(int i = 0; i<size; i++) {
            similarity[i] = ParamWeight.K_WEIGHT * kSimilarity[i] + (1 - ParamWeight.K_WEIGHT) * vSimilarity[i];
        }
        resultEntity.setSimilarity(similarity);
        return resultEntity;
    }

    /**
     * 多个算法间取综合
     * @param sourceImage
     * @param map
     * @return
     */
    public static ResultEntity multiMixSimilarityComparision(String sourceImage, Map<Algorithms, Double> map) {
        List<ResultEntity> resultEntities = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        Set<Map.Entry<Algorithms, Double>> entrySet = map.entrySet();
        for (Map.Entry<Algorithms, Double> entry : entrySet) {
            resultEntities.add(mixSimilarityComparision(sourceImage, entry.getKey()));
            weights.add(entry.getValue());
        }

        int size = resultEntities.get(0).getSize();
        ResultEntity resultEntity = new ResultEntity(size);
        resultEntity.setPath(resultEntities.get(0).getPath());
        double[] similarity = new double[size];

        // 对每一个图片,加上每个算法的权重
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < resultEntities.size(); i++) {
                similarity[j] += resultEntities.get(i).getSimilarity()[j] * weights.get(i);
            }
        }

        resultEntity.setSimilarity(similarity);
        return resultEntity;
    }

    public static void main(String[] args) {

        Map<Algorithms, Double> map = new HashMap<>();
        map.put(Algorithms.MULTIPHASH, 1.0);
        ParamWeight.K_WEIGHT = 0.5;
        ResultEntity resultEntity = multiMixSimilarityComparision("SH600012.txt", map);
        resultEntity.sort();
        int tag = resultEntity.getRank()[0].getTag();
        ResultRank[] resultRanks = resultEntity.getRank();
        int count = 1;
        for (ResultRank resultRank : resultRanks) {
            System.out.println(count++ + "@" + resultRank.getSimilarity() + "@" + resultEntity.getPath()[resultRank.getTag() - 1]);
        }
        System.out.println(resultEntity.getRank()[0].getSimilarity());
        System.out.println(resultEntity.getPath()[tag - 1]);

    }
}

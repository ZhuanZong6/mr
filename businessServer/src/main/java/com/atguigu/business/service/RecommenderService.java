package com.atguigu.business.service;

import com.atguigu.business.model.recom.Recommendation;
import com.atguigu.business.model.request.*;
import com.atguigu.business.utils.Constant;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class RecommenderService {

    // 混合推荐中CF的比例
    private static Double CF_RATING_FACTOR = 0.3;
    private static Double CONTENT_REC_RATING_FACTOR = 0.3;
    private static Double STREAM_REC_RATING_FACTOR = 0.4;

    @Autowired
    private MongoClient mongoClient;
    @Autowired
    private TransportClient esClient;

    // 协同过滤推荐（电影相似度矩阵）
    private List<Recommendation> findMovieCFRecs(int mid, int maxItems) {
        MongoCollection<Document> movieRecsCollection = mongoClient.getDatabase(Constant.MONGODB_DATABASE).getCollection(Constant.MONGODB_MOVIE_RECS_COLLECTION);
        Document movieRecs = movieRecsCollection.find(new Document("mid", mid)).first();
        return parseRecs(movieRecs, maxItems);
    }

    // 协同过滤推荐【用户电影矩阵】
    private List<Recommendation> findUserCFRecs(int uid, int maxItems) {
        MongoCollection<Document> movieRecsCollection = mongoClient.getDatabase(Constant.MONGODB_DATABASE).getCollection(Constant.MONGODB_USER_RECS_COLLECTION);
        Document userRecs = movieRecsCollection.find(new Document("uid", uid)).first();
        return parseRecs(userRecs, maxItems);
    }



    // 基于内容的推荐算法定义 109行用到
    private List<Recommendation> findContentBasedMoreLikeThisRecommendations(int mid, int maxItems) {
        MoreLikeThisQueryBuilder query = QueryBuilders.moreLikeThisQuery(/*new String[]{"name", "descri", "genres", "actors", "directors", "tags"},*/
                new MoreLikeThisQueryBuilder.Item[]{new MoreLikeThisQueryBuilder.Item(Constant.ES_INDEX, Constant.ES_MOVIE_TYPE, String.valueOf(mid))});

        return parseESResponse(esClient.prepareSearch().setQuery(query).setSize(maxItems).execute().actionGet());
    }

    // 实时推荐
    private List<Recommendation> findStreamRecs(int uid,int maxItems){
        MongoCollection<Document> streamRecsCollection = mongoClient.getDatabase(Constant.MONGODB_DATABASE).getCollection(Constant.MONGODB_STREAM_RECS_COLLECTION);
        Document streamRecs = streamRecsCollection.find(new Document("uid", uid)).first();
        return parseRecs(streamRecs, maxItems);
    }

    private List<Recommendation> parseRecs(Document document, int maxItems) {
        List<Recommendation> recommendations = new ArrayList<>();
        if (null == document || document.isEmpty())
            return recommendations;
        ArrayList<Document> recs = document.get("recs", ArrayList.class);
        for (Document recDoc : recs) {
            recommendations.add(new Recommendation(recDoc.getInteger("mid"), recDoc.getDouble("score")));
        }
        Collections.sort(recommendations, new Comparator<Recommendation>() {
            @Override
            public int compare(Recommendation o1, Recommendation o2) {
                return o1.getScore() > o2.getScore() ? -1 : 1;
            }
        });
        return recommendations.subList(0, maxItems > recommendations.size() ? recommendations.size() : maxItems);
    }

    // 全文检索
    private List<Recommendation> findContentBasedSearchRecommendations(String text, int maxItems) {
        MultiMatchQueryBuilder query = QueryBuilders.multiMatchQuery(text, "name", "descri");
        return parseESResponse(esClient.prepareSearch().setIndices(Constant.ES_INDEX).setTypes(Constant.ES_MOVIE_TYPE).setQuery(query).setSize(maxItems).execute().actionGet());
    }

    private List<Recommendation> parseESResponse(SearchResponse response) {
        List<Recommendation> recommendations = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            recommendations.add(new Recommendation((int) hit.getSourceAsMap().get("mid"), (double) hit.getScore()));
        }
        return recommendations;
    }

    // 实时推荐算法iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii
    private List<Recommendation> findStreamRecommend(int productId, int maxItems) {
        List<Recommendation> streamRecommendations = new ArrayList<>();
        //实时推荐结果
        List<Recommendation> streamRecs = findStreamRecs(productId,maxItems);
        for (Recommendation recommendation : streamRecs) {
            streamRecommendations.add(new Recommendation(recommendation.getMid(), recommendation.getScore() * STREAM_REC_RATING_FACTOR));
        }

//        //基于es内容推荐结果
//        List<Recommendation> cbRecs = findContentBasedMoreLikeThisRecommendations(productId, maxItems);
//        for (Recommendation recommendation : cbRecs) {
//            hybridRecommendations.add(new Recommendation(recommendation.getMid(), recommendation.getScore() * CONTENT_REC_RATING_FACTOR));
//        }


        Collections.sort(streamRecommendations, new Comparator<Recommendation>() {
            @Override
            public int compare(Recommendation o1, Recommendation o2) {
                return o1.getScore() > o2.getScore() ? -1 : 1;
            }
        });
        return streamRecommendations.subList(0, maxItems > streamRecommendations.size() ? streamRecommendations.size() : maxItems);
    }

//------------------用到离线推荐数据------------------
    public List<Recommendation> getCollaborativeFilteringRecommendations(MovieRecommendationRequest request) {
        return findMovieCFRecs(request.getMid(), request.getSum());
    }

    public List<Recommendation>  getCollaborativeFilteringRecommendations(UserRecommendationRequest request) {

        return findUserCFRecs(request.getUid(), request.getSum());
    }
//------------------end用到离线推荐数据------------------


//    public List<Recommendation> getContentBasedMoreLikeThisRecommendations(MovieRecommendationRequest request) {
//        return findContentBasedMoreLikeThisRecommendations(request.getMid(), request.getSum());
//    }

    public List<Recommendation> getContentBasedSearchRecommendations(SearchRecommendationRequest request) {
        return findContentBasedSearchRecommendations(request.getText(), request.getSum());
    }

    public List<Recommendation> getStreamRecommend(MovieHybridRecommendationRequest request) {
        return findStreamRecommend(request.getMid(), request.getSum());//第一个参数得到的是uid，但是该函数中除了实时推荐，都是用mid
    }


    public List<Recommendation> getHotRecommendations(HotRecommendationRequest request) {
        // 获取热门电影的条目
        MongoCollection<Document> rateMoreMoviesRecentlyCollection = mongoClient.getDatabase(Constant.MONGODB_DATABASE).getCollection(Constant.MONGODB_RATE_MORE_MOVIES_RECENTLY_COLLECTION);
        FindIterable<Document> documents = rateMoreMoviesRecentlyCollection.find().sort(Sorts.descending("yeahmonth")).limit(request.getSum());

        List<Recommendation> recommendations = new ArrayList<>();
        for (Document document : documents) {
            recommendations.add(new Recommendation(document.getInteger("mid"), 0D));
        }
        return recommendations;
    }

    public List<Recommendation> getRateMoreRecommendations(RateMoreRecommendationRequest request) {

        // 获取评分最多电影的条目
        MongoCollection<Document> rateMoreMoviesCollection = mongoClient.getDatabase(Constant.MONGODB_DATABASE).getCollection(Constant.MONGODB_RATE_MORE_MOVIES_COLLECTION);
        FindIterable<Document> documents = rateMoreMoviesCollection.find().sort(Sorts.descending("count")).limit(request.getSum());

        List<Recommendation> recommendations = new ArrayList<>();
        for (Document document : documents) {
            recommendations.add(new Recommendation(document.getInteger("mid"), 0D));
        }
        return recommendations;
    }

    public List<Recommendation> getContentBasedGenresRecommendations(SearchRecommendationRequest request) {
        FuzzyQueryBuilder query = QueryBuilders.fuzzyQuery("genres", request.getText());
        return parseESResponse(esClient.prepareSearch().setIndices(Constant.ES_INDEX).setTypes(Constant.ES_MOVIE_TYPE).setQuery(query).setSize(request.getSum()).execute().actionGet());
    }

    public List<Recommendation> getTopGenresRecommendations(TopGenresRecommendationRequest request){
        Document genresTopMovies = mongoClient.getDatabase(Constant.MONGODB_DATABASE).getCollection(Constant.MONGODB_GENRES_TOP_MOVIES_COLLECTION)
                .find(Filters.eq("genres",request.getGenres())).first();
        return parseRecs(genresTopMovies,request.getSum());
    }

}
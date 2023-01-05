package com.atguigu.business.rest;

import com.atguigu.business.model.domain.Tag;
import com.atguigu.business.model.recom.Recommendation;
import com.atguigu.business.model.request.*;
import com.atguigu.business.service.*;
import com.atguigu.business.model.domain.User;
import com.atguigu.business.utils.Constant;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Random;


@RequestMapping("/rest/movie")
@Controller
public class MovieRestApi {


    private static Logger logger = Logger.getLogger(MovieRestApi.class.getName());

    @Autowired
    private RecommenderService recommenderService;
    @Autowired
    private MovieService movieService;
    @Autowired
    private UserService userService;
    @Autowired
    private RatingService ratingService;
    @Autowired
    private TagService tagService;

    /**f
     * 实时推荐
     * @param username
     * @param model
     * @return
     */
    @RequestMapping(value = "/stream", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getStreamMovies(@RequestParam("username")String username, @RequestParam("num")int num, Model model) {
        User user = userService.findByUsername(username);                                                                      //第一个参数应该传进mid(只用改这里)
        List<Recommendation> recommendations = recommenderService.getStreamRecommend(new MovieHybridRecommendationRequest(user.getUid(),num));

        if(recommendations.size()==0){
            String randomGenres = user.getPrefGenres().get(new Random().nextInt(user.getPrefGenres().size()));
            recommendations = recommenderService.getTopGenresRecommendations(new TopGenresRecommendationRequest(randomGenres.split(" ")[0],num));

        }
        model.addAttribute("success",true);
        model.addAttribute("movies",movieService.getHybirdRecommendeMovies(recommendations));
        return model;
    }

    //--------------------用到离线推荐数据-------------------
    /**b:   user.getUid()是uid的hashcode，对应的数值在userRecs中不存在
     *  离线推荐，基于用户的协同过滤
     *  读取userRecs表
     * @param username
     * @param model
     * @return
     */
    @RequestMapping(value = "/wish", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getWishMovies(@RequestParam("username")String username,@RequestParam("num")int num, Model model) {
        User user = userService.findByUsername(username);
        //基于用户的协同过滤，读取userRecs表
        List<Recommendation> recommendations = recommenderService.getCollaborativeFilteringRecommendations(new UserRecommendationRequest(user.getUid(),num));
        if(recommendations.size()==0){
            String randomGenres = user.getPrefGenres().get(new Random().nextInt(user.getPrefGenres().size()));
            recommendations = recommenderService.getTopGenresRecommendations(new TopGenresRecommendationRequest(randomGenres.split(" ")[0],num));
        }
        model.addAttribute("success",true);
        model.addAttribute("movies",movieService.getRecommendeMovies(recommendations));
        return model;
    }

    /**f
     * 获取电影详细页面相似的电影集合
     * 读取movieRecs表
     * @param id
     * @param model
     * @return
     */
    @RequestMapping(value = "/same/{mid}", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getSameMovie(@PathVariable("mid")int id,@RequestParam("num")int num, Model model) {
        //读取movieRecs表
        List<Recommendation> recommendations = recommenderService.getCollaborativeFilteringRecommendations(new MovieRecommendationRequest(id,num));
        model.addAttribute("success",true);
        model.addAttribute("movies",movieService.getRecommendeMovies(recommendations));
        return model;
    }
    //--------------------end用到离线推荐数据-------------------


    /**f
     * 获取热门推荐
     * @param model
     * @return
     */
    @RequestMapping(value = "/hot", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getHotMovies(@RequestParam("num")int num, Model model) {
        //读取最近一个月被评分最多的表
        List<Recommendation> recommendations = recommenderService.getHotRecommendations(new HotRecommendationRequest(num));
        model.addAttribute("success",true);
        model.addAttribute("movies",movieService.getRecommendeMovies(recommendations));
        return model;
    }

    /**f
     * 获取投票最多的电影 num：数量
     * @param model
     * @return
     */
    @RequestMapping(value = "/rate", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getRateMoreMovies(@RequestParam("num")int num, Model model) {
        //读取历史被评分最多的表
        List<Recommendation> recommendations = recommenderService.getRateMoreRecommendations(new RateMoreRecommendationRequest(num));
        model.addAttribute("success",true);
        model.addAttribute("movies",movieService.getRecommendeMovies(recommendations));
        return model;
    }

    /**f
     * 获取新添加的电影，num：数量
     * @param model
     * @return
     */
    @RequestMapping(value = "/new", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getNewMovies(@RequestParam("num")int num, Model model) {
        model.addAttribute("success",true);
        model.addAttribute("movies",movieService.getNewMovies(new NewRecommendationRequest(num)));
        return model;
    }




    /**f
     * 获取单个电影的信息
     * @param id
     * @param model
     * @return
     */
    @RequestMapping(value = "/info/{mid}", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getMovieInfo(@PathVariable("mid")int id, Model model) {
        model.addAttribute("success",true);
        model.addAttribute("movie",movieService.findByMID(id));
        return model;
    }

    /**f
     * 模糊查询电影
     * @param query
     * @param model
     * @return
     */
    @RequestMapping(value = "/search", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getSearchMovies(@RequestParam("query")String query, Model model) {
        List<Recommendation> recommendations = recommenderService.getContentBasedSearchRecommendations(new SearchRecommendationRequest(query,50));
        model.addAttribute("success",true);
        model.addAttribute("movies",movieService.getRecommendeMovies(recommendations));
        return model;
    }

    /**f
     * 查询电影类别
     * @param category
     * @param model
     * @return
     */
    @RequestMapping(value = "/genres", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getGenresMovies(@RequestParam("category")String category, Model model) {
        List<Recommendation> recommendations = recommenderService.getContentBasedGenresRecommendations(new SearchRecommendationRequest(category,50));
        model.addAttribute("success",true);
        model.addAttribute("movies",movieService.getRecommendeMovies(recommendations));
        return model;
    }

    /**
     * 获取用户评分过得电影
     * @param username
     * @param model
     * @return
     */
    @RequestMapping(value = "/myrate", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getMyRateMovies(@RequestParam("username")String username, Model model) {
        User user = userService.findByUsername(username);
        model.addAttribute("success",true);
        model.addAttribute("movies",movieService.getMyRateMovies(user.getUid()));
        return model;
    }

    //给电影评分
    @RequestMapping(value = "/rate/{mid}", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model rateToMovie(@PathVariable("mid")int id,@RequestParam("score")Double score,@RequestParam("username")String username, Model model) {
        User user = userService.findByUsername(username);
        MovieRatingRequest request = new MovieRatingRequest(user.getUid(),id,score);
        boolean complete = ratingService.movieRating(request);
        //埋点日志***************************************************************
        if(complete) {
            System.out.print("=========complete=========");
            logger.info(Constant.MOVIE_RATING_PREFIX + ":" + user.getUid() +"|"+ id +"|"+ request.getScore() +"|"+ System.currentTimeMillis()/1000);
        }
        model.addAttribute("success",true);
        model.addAttribute("message"," 已完成评分！");
        return model;
    }


    //获取电影的所有标签
    @RequestMapping(value = "/tag/{mid}", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getMovieTags(@PathVariable("mid")int mid, Model model) {
        model.addAttribute("success",true);
        model.addAttribute("tags",tagService.findMovieTags(mid));
        return model;
    }

    @RequestMapping(value = "/mytag/{mid}", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getMyTags(@PathVariable("mid")int mid,@RequestParam("username")String username, Model model) {
        User user = userService.findByUsername(username);
        model.addAttribute("success",true);
        model.addAttribute("tags",tagService.findMyMovieTags(user.getUid(),mid));
        return model;
    }


    //给电影打标签
    @RequestMapping(value = "/newtag/{mid}", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model addMyTags(@PathVariable("mid")int mid,@RequestParam("tagname")String tagname,@RequestParam("username")String username, Model model) {
        User user = userService.findByUsername(username);
        Tag tag = new Tag(user.getUid(),mid,tagname);
        tagService.newTag(tag);
        model.addAttribute("success",true);
        model.addAttribute("tag",tag);
        return model;
    }

    @RequestMapping(value = "/stat", produces = "application/json", method = RequestMethod.GET )
    @ResponseBody
    public Model getMyRatingStat(@RequestParam("username")String username, Model model) {
        User user = userService.findByUsername(username);
        model.addAttribute("success",true);
        model.addAttribute("stat",ratingService.getMyRatingStat(user));
        return model;
    }

}

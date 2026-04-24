package com.hmdp.repository;

import com.hmdp.entity.BlogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlogRepository extends ElasticsearchRepository<BlogDocument, Long> {

    List<BlogDocument> findByTitleContainingOrContentContaining(String title, String content);
}

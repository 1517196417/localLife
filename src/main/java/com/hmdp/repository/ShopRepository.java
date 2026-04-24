package com.hmdp.repository;

import com.hmdp.entity.ShopDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShopRepository extends ElasticsearchRepository<ShopDocument, Long> {

    List<ShopDocument> findByNameContainingOrAddressContainingOrAreaContaining(String name, String address, String area);
}

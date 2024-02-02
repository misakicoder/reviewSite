package com.liyu;

import com.liyu.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;

    @Test
    public void test123(){
        shopService.saveShop2Redis(1L,10L);
    }
}

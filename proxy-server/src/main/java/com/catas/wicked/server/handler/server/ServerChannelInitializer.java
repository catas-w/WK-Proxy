package com.catas.wicked.server.handler.server;

import com.catas.wicked.common.bean.IdGenerator;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.provider.CertManager;
import com.catas.wicked.server.handler.RearHttpAggregator;
import com.catas.wicked.server.strategy.DefaultSkipPredicate;
import com.catas.wicked.server.strategy.StrategyList;
import com.catas.wicked.server.strategy.StrategyManager;
import com.catas.wicked.server.strategy.TailStrategyManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpServerCodec;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static com.catas.wicked.server.strategy.Handler.*;

@Slf4j
@Singleton
public class ServerChannelInitializer extends ChannelInitializer {

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private MessageQueue messageQueue;

    @Inject
    private IdGenerator idGenerator;

    @Inject
    private CertManager certManager;

    @Inject
    @Named("tail")
    private StrategyManager strategyManager;

    @Override
    protected void initChannel(Channel ch) throws Exception {
        StrategyList strategyList = defaultStrategyList();
        ch.pipeline().addLast(HTTP_CODEC.name(), strategyList.getSupplier(HTTP_CODEC.name()).get());
        ch.pipeline().addLast(SERVER_STRATEGY.name(), strategyList.getSupplier(SERVER_STRATEGY.name()).get());
        ch.pipeline().addLast(PREV_RECORDER.name(), strategyList.getSupplier(PREV_RECORDER.name()).get());
        ch.pipeline().addLast(SERVER_PROCESSOR.name(), strategyList.getSupplier(SERVER_PROCESSOR.name()).get());
        ch.pipeline().addLast(POST_RECORDER.name(), strategyList.getSupplier(POST_RECORDER.name()).get());
    }

    private StrategyList defaultStrategyList() {
        StrategyList list = new StrategyList();
        list.add(SSL_HANDLER.name(), false, () -> null);
        list.add(HTTP_CODEC.name(), true, HttpServerCodec::new);
        list.addAnchored(SERVER_STRATEGY.name(), () -> new ServerStrategyHandler(
                appConfig, certManager, idGenerator, defaultStrategyList(), strategyManager));
        list.addAnchored(PREV_RECORDER.name(), () -> new ServerPreRecorder(appConfig, messageQueue));
        list.add(THROTTLE_HANDLER.name(), false, () -> null);
        list.addAnchored(SERVER_PROCESSOR.name(), () -> new ServerProcessHandler(appConfig, messageQueue, strategyManager));
        list.add(HTTP_AGGREGATOR.name(), false,
                () -> new RearHttpAggregator(appConfig.getMaxContentSize()));
        list.addAnchored(POST_RECORDER.name(), () -> new ServerPostRecorder(appConfig, messageQueue));
        list.add(new TailStrategyManager.TailContextStrategy());

        list.getList().forEach(model -> model.setSkipPredicate(DefaultSkipPredicate.INSTANCE));
        return list;
    }
}

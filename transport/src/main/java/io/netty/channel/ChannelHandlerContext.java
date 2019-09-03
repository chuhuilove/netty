/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import io.netty.util.concurrent.EventExecutor;

import java.nio.channels.Channels;

/**
 * Enables a {@link ChannelHandler} to interact with its {@link ChannelPipeline}
 * and other handlers. Among other things a handler can notify the next {@link ChannelHandler} in the
 * {@link ChannelPipeline} as well as modify the {@link ChannelPipeline} it belongs to dynamically.
 *
 * 允许{@link ChannelHandler}与其{@link ChannelPipeline}和其他处理程序进行交互.
 * 除此之外,处理程序还可以通知{@link ChannelPipeline}中的下一个{@link ChannelHandler}以及动态修改它所属的{@link ChannelPipeline}.
 *
 * <h3>Notify</h3>
 *
 * 您可以通过调用此处提供的各种方法之一,在同一{@link ChannelPipeline}中通知最近的handler.
 * 请参考{@link ChannelPipeline}来理解事件如何流动的.
 *
 * <h3>修改pipeline</h3>
 *
 * 您可以通过调用{@link #pipeline()}获取你的handler所属的{@link ChannelPipeline}.
 * 应用程序可以在运行时动态地插入、删除或替换pipeline中的handler.
 *
 * <h3>Retrieving for later use</h3>
 *
 * 你可以保留{@link ChannelHandlerContext}供以后使用,例如在处理程序方法之外触发事件,甚至可以从不同的线程触发.
 * <pre>
 * public class MyHandler extends {@link ChannelDuplexHandler} {
 *
 *     <b>private {@link ChannelHandlerContext} ctx;</b>
 *
 *     public void beforeAdd({@link ChannelHandlerContext} ctx) {
 *         <b>this.ctx = ctx;</b>
 *     }
 *
 *     public void login(String username, password) {
 *         ctx.write(new LoginMessage(username, password));
 *     }
 *     ...
 * }
 * </pre>
 *
 * <h3>存储状态信息</h3>
 *
 * {@link #attr(AttributeKey)} allow you to
 * store and access stateful information that is related with a handler and its
 * context.  Please refer to {@link ChannelHandler} to learn various recommended
 * ways to manage stateful information.
 *
 * {@link #attr(AttributeKey)}允许您存储和访问与handler及其上下文相关的有状态信息.
 * 请参考{@link ChannelHandler}以了解管理有状态信息的各种推荐方式.
 *
 *
 * <h3>一个handler可以有多个context</h3>
 *
 * 请注意,一个{@link ChannelHandler}实例可以被添加到多个{@link ChannelPipeline}.
 * 这意味着单个{@link ChannelHandler}实例可以有多个{@link ChannelHandlerContext},
 * 因此,单实例一旦被添加到多个{@link ChannelPipeline}后,就可以被多个不同的{@link ChannelHandlerContext}调用.
 *
 * <p>
 * For example, the following handler will have as many independent {@link AttributeKey}s
 * as how many times it is added to pipelines, regardless if it is added to the
 * same pipeline multiple times or added to different pipelines multiple times:
 *
 * 例如,以下处理程序将具有与pipeline添加的次数一样多的独立{@link AttributeKey},
 * 无论它是多次添加到同一pipeline还是多次添加到不同pipeline：
 *
 * <pre>
 * public class FactorialHandler extends {@link ChannelInboundHandlerAdapter} {
 *
 *   private final {@link AttributeKey}&lt;{@link Integer}&gt; counter = {@link AttributeKey}.valueOf("counter");
 *
 *   // 此处理程序将从1开始接收一系列递增的整数
 *   {@code @Override}
 *   public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     Integer a = ctx.attr(counter).get();
 *
 *     if (a == null) {
 *       a = 1;
 *     }
 *
 *     attr.set(a * (Integer) msg);
 *   }
 * }
 *
 * // Different context objects are given to "f1", "f2", "f3", and "f4" even if
 * // they refer to the same handler instance.  Because the FactorialHandler
 * // stores its state in a context object (using an {@link AttributeKey}), the factorial is
 * // calculated correctly 4 times once the two pipelines (p1 and p2) are active.
 * //
 * // 不同的上下文对象被赋予"f1","f2","f3"和"f4",即使它们引用相同的handler实例.
 * // 因为FactorialHandler将其状态存储在上下文对象中(使用{@link AttributeKey}),
 * // 所以当两个管道(p1和p2)处于活动状态时,factorial将被正确计算4次.
 * //
 * FactorialHandler fh = new FactorialHandler();
 *
 * {@link ChannelPipeline} p1 = {@link Channels}.pipeline();
 * p1.addLast("f1", fh);
 * p1.addLast("f2", fh);
 *
 * {@link ChannelPipeline} p2 = {@link Channels}.pipeline();
 * p2.addLast("f3", fh);
 * p2.addLast("f4", fh);
 * </pre>
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 */
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {

    /**
     * Return the {@link Channel} which is bound to the {@link ChannelHandlerContext}.
     */
    Channel channel();

    /**
     * Returns the {@link EventExecutor} which is used to execute an arbitrary task.
     */
    EventExecutor executor();

    /**
     * The unique name of the {@link ChannelHandlerContext}.The name was used when then {@link ChannelHandler}
     * was added to the {@link ChannelPipeline}. This name can also be used to access the registered
     * {@link ChannelHandler} from the {@link ChannelPipeline}.
     */
    String name();

    /**
     * The {@link ChannelHandler} that is bound this {@link ChannelHandlerContext}.
     */
    ChannelHandler handler();

    /**
     * Return {@code true} if the {@link ChannelHandler} which belongs to this context was removed
     * from the {@link ChannelPipeline}. Note that this method is only meant to be called from with in the
     * {@link EventLoop}.
     */
    boolean isRemoved();

    @Override
    ChannelHandlerContext fireChannelRegistered();

    @Override
    ChannelHandlerContext fireChannelUnregistered();

    @Override
    ChannelHandlerContext fireChannelActive();

    @Override
    ChannelHandlerContext fireChannelInactive();

    @Override
    ChannelHandlerContext fireExceptionCaught(Throwable cause);

    @Override
    ChannelHandlerContext fireUserEventTriggered(Object evt);

    @Override
    ChannelHandlerContext fireChannelRead(Object msg);

    @Override
    ChannelHandlerContext fireChannelReadComplete();

    @Override
    ChannelHandlerContext fireChannelWritabilityChanged();

    @Override
    ChannelHandlerContext read();

    @Override
    ChannelHandlerContext flush();

    /**
     * Return the assigned {@link ChannelPipeline}
     */
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     */
    ByteBufAllocator alloc();

    /**
     * @deprecated Use {@link Channel#attr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> Attribute<T> attr(AttributeKey<T> key);

    /**
     * @deprecated Use {@link Channel#hasAttr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> boolean hasAttr(AttributeKey<T> key);
}

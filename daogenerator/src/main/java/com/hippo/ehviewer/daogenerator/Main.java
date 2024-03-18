/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.daogenerator;

public class Main {

    /**
     * 随着项目逐渐迁移，此任务将被弃用，dao类将在项目目录中明文保存，而不是在每次打包时重新生成
     * 以后改为手动执行dao类更新
     * 祖宗之法不可变啊ToT
     * @param args main 入参
     */
    public static void main(String[] args){
        try {
            EhDaoGenerator.generate();
        }catch (Throwable t){
            System.out.println(t);
        }
    }
}
